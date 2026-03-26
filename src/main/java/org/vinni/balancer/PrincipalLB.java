package org.vinni.balancer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PrincipalLB extends JFrame {
    private ServerSocket lbSocket;
    private final List<Integer> nodosActivos = new CopyOnWriteArrayList<>();
    private JTextArea logArea = new JTextArea();
    private boolean encendido = false;

    public PrincipalLB() {
        setTitle("Balanceador Central - Proxy Activo");
        JButton btnOn = new JButton("Encender");
        JButton btnOff = new JButton("Apagar");
        JPanel panelBotones = new JPanel();
        panelBotones.add(btnOn); panelBotones.add(btnOff);
        add(panelBotones, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        setSize(400, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        btnOn.addActionListener(e -> iniciar());
        btnOff.addActionListener(e -> apagar());
    }

    private void iniciar() {
        if (encendido) return;
        encendido = true;
        new Thread(() -> {
            try {
                lbSocket = new ServerSocket(12345);
                log("[SISTEMA] Balanceador en puerto 12345");
                while (encendido) {
                    Socket s = lbSocket.accept();
                    new Thread(() -> gestionarPeticion(s)).start();
                }
            } catch (IOException e) { if (encendido) log("Error: " + e.getMessage()); }
        }).start();
    }

    private void gestionarPeticion(Socket cliente) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            String protocolo = in.readLine();
            if (protocolo == null) return;

            if ("REGISTRO_SRV".equals(protocolo)) {
                int puerto = Integer.parseInt(in.readLine());
                if (!nodosActivos.contains(puerto)) {
                    nodosActivos.add(puerto);
                    log("[NODO] Registrado: " + puerto);
                }
                cliente.close();
            } else if ("GET_NODOS".equals(protocolo)) {
                new PrintWriter(cliente.getOutputStream(), true).println(nodosActivos.toString());
                cliente.close();
            } else {
                redirigirConFailover(cliente, protocolo);
            }
        } catch (IOException e) { log("Error peticion: " + e.getMessage()); }
    }

    private void redirigirConFailover(Socket cliente, String nombre) {
        int puertoDestino = -1;
        while (!nodosActivos.isEmpty()) {
            int index = new Random().nextInt(nodosActivos.size());
            int p = nodosActivos.get(index);
            try {
                Socket srv = new Socket("localhost", p);
                new PrintWriter(srv.getOutputStream(), true).println(nombre);

                // Iniciamos el puente en ambos sentidos
                // IMPORTANTE: Si un hilo termina, debe cerrar el otro socket
                new Thread(() -> bridge(cliente, srv)).start();
                new Thread(() -> bridge(srv, cliente)).start();

                log("[OK] " + nombre + " -> Nodo " + p);
                return;
            } catch (IOException e) {
                log("[!] Nodo " + p + " caído. Reintentando...");
                nodosActivos.remove(index);
            }
        }
        try { cliente.close(); } catch (IOException e) {}
    }

    private void bridge(Socket entrada, Socket salida) {
        try (InputStream in = entrada.getInputStream();
             OutputStream out = salida.getOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // Si hay error en un sentido, forzamos el cierre del otro para notificar al cliente
        } finally {
            try { entrada.close(); salida.close(); } catch (IOException ex) {}
        }
    }

    private void apagar() { encendido = false; try { if (lbSocket != null) lbSocket.close(); nodosActivos.clear(); } catch (IOException e) {} }
    private void log(String m) { SwingUtilities.invokeLater(() -> logArea.append(m + "\n")); }
    public static void main(String[] args) { new PrincipalLB().setVisible(true); }
}