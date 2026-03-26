package org.vinni.servidor.gui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PrincipalSrv extends JFrame {
    private int miPuerto;
    private final Map<String, PrintWriter> clientesLocales = new ConcurrentHashMap<>();
    private JTextArea logArea = new JTextArea();
    private JButton btnIniciar = new JButton("Encender Servidor");
    private boolean registrado = false;

    public PrincipalSrv() {
        setTitle("Nodo Servidor - Java Spring Style");
        btnIniciar.addActionListener(e -> encenderServidor());
        setLayout(new BorderLayout());
        add(btnIniciar, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
        setSize(400, 350);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void encenderServidor() {
        btnIniciar.setEnabled(false);
        autoConfigurar();
        new Thread(() -> intentarRegistro(1)).start();
    }

    private void autoConfigurar() {
        for (int p = 12346; p < 12360; p++) {
            try {
                ServerSocket ss = new ServerSocket(p);
                miPuerto = p;
                setTitle("Nodo Servidor: " + p);
                new Thread(() -> {
                    try {
                        while (true) {
                            Socket s = ss.accept();
                            new Thread(new Manejador(s)).start();
                        }
                    } catch (Exception e) {
                        log("[!] Error en socket maestro: " + e.getMessage());
                    }
                }).start();
                break;
            } catch (IOException e) { }
        }
    }

    private void intentarRegistro(int intento) {
        if (registrado) return;
        if (intento > 3) {
            log("[!] Balanceador no detectado tras 3 intentos. Reintentando en 10s...");
            reprogramarRegistro(1, 10000);
            return;
        }
        try (Socket s = new Socket("localhost", 12345);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("REGISTRO_SRV");
            out.println(miPuerto);
            log("[INFO] Registro exitoso en puerto " + miPuerto);
            registrado = true;
            new Thread(() -> {
                while (registrado) {
                    try {
                        Thread.sleep(15000);
                        try (Socket test = new Socket("localhost", 12345)) { }
                    } catch (Exception e) {
                        log("[ALERTA] Conexión con Balanceador perdida. Recuperando registro...");
                        registrado = false;
                        intentarRegistro(1);
                    }
                }
            }).start();
        } catch (Exception e) {
            log("[ALERTA] Balanceador fuera de línea. Intento " + intento + "/3...");
            reprogramarRegistro(intento + 1, 3000);
        }
    }

    private void reprogramarRegistro(int intento, int delay) {
        new Thread(() -> {
            try { Thread.sleep(delay); intentarRegistro(intento); } catch (Exception ex) {}
        }).start();
    }

    private void sincronizarUsuariosGlobales() {
        // Llamar siempre dentro de synchronized(clientesLocales)
        String trama = "LISTA:" + String.join(",", clientesLocales.keySet());
        clientesLocales.values().forEach(p -> p.println(trama));
    }

    private class Manejador implements Runnable {
        private final Socket s;
        public Manejador(Socket s) { this.s = s; }

        public void run() {
            String nombre = null;
            PrintWriter out = null;

            try {
                // ── IMPORTANTE: el LB ya envió el nombre por el stream antes
                // de iniciar el bridge. Aquí leemos con BufferedReader sobre el
                // InputStream del socket, que recibe exactamente lo que el LB
                // escribió con println(nombre). El bridge empieza DESPUÉS de que
                // el LB llamó srvOut.println(nombre), así que no hay carreras
                // entre el nombre y el payload del cliente. ──
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);

                nombre = in.readLine();
                if (nombre == null) return;

                if (nombre.startsWith("INTERNAL:")) {
                    procesarInterno(nombre.substring(9));
                    return;
                }

                // Registro atómico: put + sincronizar en un solo bloque crítico
                synchronized (clientesLocales) {
                    clientesLocales.put(nombre, out);
                    sincronizarUsuariosGlobales();
                }
                log("[SISTEMA] Cliente conectado: " + nombre);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("@")) enviarPrivado(nombre, line);
                    else broadcast(nombre + ": " + line, true);
                }

            } catch (Exception e) {
                log("[SISTEMA] Desconexión: " + (nombre != null ? nombre : "Desconocido"));
            } finally {
                // Desregistro atómico + liberar recursos
                if (nombre != null) {
                    synchronized (clientesLocales) {
                        clientesLocales.remove(nombre);
                        sincronizarUsuariosGlobales();
                    }
                }
                if (out != null) out.close();
                try { s.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void enviarPrivado(String de, String trama) {
        try {
            int esp = trama.indexOf(" ");
            if (esp == -1) return;
            String para = trama.substring(1, esp);
            String msg  = trama.substring(esp + 1);
            if (clientesLocales.containsKey(para)) {
                clientesLocales.get(para).println("[P] " + de + ": " + msg);
            } else {
                enviarAMesh("INTERNAL:PV#" + de + "#" + para + "#" + msg);
            }
        } catch (Exception e) {
            log("[!] Error en envío privado: " + e.getMessage());
        }
    }

    private void broadcast(String m, boolean replica) {
        SwingUtilities.invokeLater(() -> logArea.append(m + "\n"));
        clientesLocales.values().forEach(p -> p.println(m));
        if (replica) enviarAMesh("INTERNAL:BC#" + m);
    }

    private void enviarAMesh(String trama) {
        try (Socket s = new Socket("localhost", 12345);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("GET_NODOS");
            String respuesta = in.readLine();
            if (respuesta == null) return;
            String lista = respuesta.replace("[", "").replace("]", "").replace(" ", "");
            for (String pStr : lista.split(",")) {
                if (pStr.isEmpty()) continue;
                int p = Integer.parseInt(pStr);
                if (p == miPuerto) continue;
                try (Socket s2 = new Socket("localhost", p);
                     PrintWriter out2 = new PrintWriter(s2.getOutputStream(), true)) {
                    out2.println(trama);
                } catch (Exception e) { }
            }
        } catch (Exception e) { }
    }

    private void procesarInterno(String t) {
        String[] p = t.split("#");
        if (p[0].equals("BC")) broadcast(p[1], false);
        else if (p[0].equals("PV") && clientesLocales.containsKey(p[2])) {
            clientesLocales.get(p[2]).println("[P] " + p[1] + ": " + p[3]);
        }
    }

    private void log(String m) { SwingUtilities.invokeLater(() -> logArea.append(m + "\n")); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}