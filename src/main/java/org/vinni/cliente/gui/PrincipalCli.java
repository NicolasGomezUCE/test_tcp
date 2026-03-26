package org.vinni.cliente.gui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class PrincipalCli extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private DefaultListModel<String> modelo = new DefaultListModel<>();
    private JList<String> listaUI = new JList<>(modelo);
    private JTextArea area = new JTextArea();
    private JTextField campo = new JTextField();
    private JButton btnCon = new JButton("Conectar");
    private String nombre;

    // ── Control de reconexión ──────────────────────────────────────────────────
    private volatile boolean reconectandose = false;
    private volatile int intentosReconexion = 0;
    private static final int MAX_RECONEXIONES = 3;

    public PrincipalCli() {
        setTitle("Cliente Resiliente");
        btnCon.addActionListener(e -> iniciarConexionManual());
        campo.addActionListener(e -> enviar());

        setLayout(new BorderLayout());
        add(btnCon, BorderLayout.NORTH);
        add(new JScrollPane(listaUI), BorderLayout.WEST);
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(campo, BorderLayout.SOUTH);

        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    // ── Entrada manual (botón) ─────────────────────────────────────────────────

    private void iniciarConexionManual() {
        intentosReconexion = 0;
        reconectandose = false;
        btnCon.setEnabled(false);
        conectar();
    }

    // ── Conexión ───────────────────────────────────────────────────────────────

    private void conectar() {
        if (nombre == null) {
            nombre = JOptionPane.showInputDialog(this, "Nombre:");
        }
        if (nombre == null || nombre.isBlank()) {
            nombre = null;
            SwingUtilities.invokeLater(() -> btnCon.setEnabled(true));
            return;
        }

        new Thread(() -> {
            try {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (IOException ignored) {}
                }

                socket = new Socket("localhost", 12345);
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println(nombre);

                // Conexión TCP exitosa: resetear contadores
                intentosReconexion = 0;
                reconectandose = false;
                SwingUtilities.invokeLater(() -> {
                    area.append("[SISTEMA] Conectado exitosamente.\n");
                    btnCon.setEnabled(false);
                });

                escuchar(new BufferedReader(new InputStreamReader(socket.getInputStream())));

            } catch (Exception e) {
                manejarFallo("[!] No se pudo conectar al servidor");
            }
        }).start();
    }

    // ── Bucle de lectura ──────────────────────────────────────────────────────

    private void escuchar(BufferedReader in) {
        try {
            String linea;
            while ((linea = in.readLine()) != null) {
                final String l = linea;

                if (l.contains("No hay servidores disponibles")) {
                    SwingUtilities.invokeLater(() -> area.append("[!] " + l + "\n"));
                    continue; // LB cerrará el socket: readLine() devolverá null
                }

                if (l.startsWith("LISTA:")) {
                    actualizarLista(l.substring(6));
                } else {
                    SwingUtilities.invokeLater(() -> area.append(l + "\n"));
                }
            }
            SwingUtilities.invokeLater(() -> area.append("[!] Conexión cerrada por el servidor.\n"));

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> area.append("[!] Conexión interrumpida.\n"));
        } finally {
            manejarFallo(null);
        }
    }

    // ── Gestión centralizada de fallos y reconexión automática ────────────────

    private void manejarFallo(String mensaje) {
        if (mensaje != null) {
            SwingUtilities.invokeLater(() -> area.append(mensaje + "\n"));
        }

        synchronized (this) {
            if (reconectandose) return;
            reconectandose = true;
        }

        intentosReconexion++;

        if (intentosReconexion > MAX_RECONEXIONES) {
            // ── 3 intentos agotados: pasar a modo manual ──────────────────
            reconectandose = false;
            SwingUtilities.invokeLater(() -> {
                area.append("[SISTEMA] Sin conexión tras " + MAX_RECONEXIONES + " intentos automáticos.\n");
                area.append("[SISTEMA] Pulse 'Conectar' para reintentar manualmente.\n");
                btnCon.setEnabled(true); // habilitar botón manual
            });
            return;
        }

        // ── Aún quedan intentos: esperar 3s y reconectar ──────────────────
        final int intento = intentosReconexion;
        SwingUtilities.invokeLater(() ->
                area.append("[SISTEMA] Reconectando automáticamente... ("
                        + intento + "/" + MAX_RECONEXIONES + ") en 3s\n"));

        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            reconectandose = false;
            conectar();
        }).start();
    }

    // ── Envío de mensajes ─────────────────────────────────────────────────────

    private void enviar() {
        String m = campo.getText().trim();
        if (m.isEmpty() || out == null) return;

        try {
            String dest = listaUI.getSelectedValue();
            if (dest != null) {
                out.println("@" + dest + " " + m);
                SwingUtilities.invokeLater(() ->
                        area.append("[Privado para " + dest + "]: " + m + "\n"));
            } else {
                out.println(m);
            }

            if (out.checkError()) throw new IOException("Error de escritura en el socket");
            SwingUtilities.invokeLater(() -> campo.setText(""));

        } catch (Exception e) {
            SwingUtilities.invokeLater(() ->
                    area.append("[SISTEMA] Error al enviar. Intentando recuperar conexión...\n"));
            manejarFallo(null);
        }
    }

    // ── Lista de usuarios ─────────────────────────────────────────────────────

    private void actualizarLista(String d) {
        SwingUtilities.invokeLater(() -> {
            modelo.clear();
            if (d == null || d.isBlank()) return;
            for (String u : d.split(",")) {
                if (!u.isBlank() && !u.equals(nombre)) modelo.addElement(u);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}