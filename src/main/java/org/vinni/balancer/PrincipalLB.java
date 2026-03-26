package org.vinni.balancer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class PrincipalLB extends JFrame {
    private ServerSocket lbSocket;
    private final List<Integer> nodosActivos = new CopyOnWriteArrayList<>();
    private JTextArea logArea = new JTextArea();
    private boolean encendido = false;

    // Round-robin en lugar de random: garantiza distribución uniforme
    private final AtomicInteger turno = new AtomicInteger(0);

    public PrincipalLB() {
        setTitle("Balanceador Central - Proxy Activo");
        JButton btnOn = new JButton("Encender");
        JButton btnOff = new JButton("Apagar");
        JPanel panelBotones = new JPanel();
        panelBotones.add(btnOn);
        panelBotones.add(btnOff);
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
            } catch (IOException e) {
                if (encendido) log("Error: " + e.getMessage());
            }
        }).start();
    }

    private void gestionarPeticion(Socket cliente) {
        try {
            // ── FIX: usar BufferedReader con un stream aparte solo para leer
            // el protocolo inicial. El bridge posterior trabaja con streams crudos
            // del socket original, evitando que el BufferedReader se "coma" bytes
            // que pertenecen al payload del cliente. ──
            // Para eso, leemos SOLO la primera línea con un InputStream manual,
            // sin envolver en BufferedReader, para no consumir bytes de más.
            String protocolo = leerLineaRaw(cliente.getInputStream());
            if (protocolo == null) { cliente.close(); return; }

            if ("REGISTRO_SRV".equals(protocolo)) {
                String puertStr = leerLineaRaw(cliente.getInputStream());
                if (puertStr == null) { cliente.close(); return; }
                int puerto = Integer.parseInt(puertStr.trim());
                if (!nodosActivos.contains(puerto)) {
                    nodosActivos.add(puerto);
                    log("[NODO] Registrado: " + puerto);
                }
                cliente.close();

            } else if ("GET_NODOS".equals(protocolo)) {
                new PrintWriter(cliente.getOutputStream(), true).println(nodosActivos.toString());
                cliente.close();

            } else {
                // protocolo == nombre del cliente
                if (nodosActivos.isEmpty()) {
                    new PrintWriter(cliente.getOutputStream(), true)
                            .println("[SISTEMA] No hay servidores disponibles en este momento.");
                    log("[!] Cliente rechazado: No hay nodos activos.");
                    cliente.close();
                    return;
                }
                redirigirConFailover(cliente, protocolo);
            }

        } catch (IOException e) {
            log("Error peticion: " + e.getMessage());
        }
    }

    /**
     * Lee una línea del InputStream byte a byte, sin BufferedReader.
     * Esto evita que se consuman bytes del payload que luego van al bridge.
     */
    private String leerLineaRaw(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') continue; // ignorar CR de CRLF
            sb.append((char) b);
        }
        return b == -1 && sb.length() == 0 ? null : sb.toString();
    }

    private void redirigirConFailover(Socket cliente, String nombre) {
        // Snapshot de la lista en este momento para iterar de forma segura
        List<Integer> snapshot = new ArrayList<>(nodosActivos);

        // Round-robin sobre el snapshot: distribuye la carga uniformemente
        int intentos = snapshot.size();
        for (int i = 0; i < intentos; i++) {
            // turno.getAndIncrement() es atómico → seguro con múltiples hilos
            int index = Math.abs(turno.getAndIncrement() % snapshot.size());
            int p = snapshot.get(index);

            try {
                Socket srv = new Socket("localhost", p);

                // ── FIX: enviar el nombre al servidor con PrintWriter sobre
                // el OutputStream crudo ANTES de iniciar el bridge.
                // El bridge copia bytes crudos, así que el nombre ya fue
                // consumido del lado cliente por leerLineaRaw(). El servidor
                // lo lee con su propio BufferedReader sobre su stream. ──
                PrintWriter srvOut = new PrintWriter(srv.getOutputStream(), true);
                srvOut.println(nombre);

                // Bridge bidireccional: cada sentido en su propio hilo.
                // Cuando uno termina, cierra ambos sockets para notificar al otro.
                Thread t1 = new Thread(() -> bridge(cliente, srv, p));
                Thread t2 = new Thread(() -> bridge(srv, cliente, p));
                t1.setDaemon(true);
                t2.setDaemon(true);
                t1.start();
                t2.start();

                log("[OK] " + nombre + " -> Nodo " + p);
                return;

            } catch (IOException e) {
                // ── FIX: eliminar por VALOR, no por índice.
                // remove(Object) es seguro aunque otro hilo modifique la lista. ──
                nodosActivos.remove(Integer.valueOf(p));
                log("[!] Nodo " + p + " caído. Eliminado de la lista activa.");
            }
        }

        // Ningún nodo disponible respondió
        try {
            new PrintWriter(cliente.getOutputStream(), true)
                    .println("[SISTEMA] No hay servidores disponibles en este momento.");
            cliente.close();
        } catch (IOException ignored) {}
        log("[!] No se pudo conectar a ningún nodo para: " + nombre);
    }

    private void bridge(Socket entrada, Socket salida, int puertNodo) {
        try (InputStream in = entrada.getInputStream();
             OutputStream out = salida.getOutputStream()) {
            byte[] buf = new byte[4096]; // buffer más grande = menos syscalls
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            // Error en el bridge: puede indicar que el nodo cayó DESPUÉS de conectar.
            // ── FIX: intentar eliminar el nodo de la lista activa si el error
            // viene del lado servidor (salida), no del cliente (entrada). ──
            if (!salida.isClosed() && salida.getPort() != 12345) {
                nodosActivos.remove(Integer.valueOf(puertNodo));
                log("[!] Nodo " + puertNodo + " caído durante sesión. Removido.");
            }
        } finally {
            // Cerrar ambos sockets para que el bridge del otro sentido también termine
            try { entrada.close(); } catch (IOException ignored) {}
            try { salida.close(); } catch (IOException ignored) {}
        }
    }

    private void apagar() {
        encendido = false;
        try { if (lbSocket != null) lbSocket.close(); } catch (IOException ignored) {}
        nodosActivos.clear();
    }

    private void log(String m) { SwingUtilities.invokeLater(() -> logArea.append(m + "\n")); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalLB().setVisible(true));
    }
}