package org.vinni.servidor.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PrincipalSrv extends JFrame {

    private int miPuerto;
    private final Map<String, PrintWriter> clientesLocales = new ConcurrentHashMap<>();
    private JTextArea logArea  = new JTextArea();
    private JButton btnIniciar = new JButton("▶  Encender Servidor");
    private boolean registrado = false;

    private static final long MI_PID = obtenerPID();

    // ── Paleta ────────────────────────────────────────────────────────────────
    private static final Color BG     = new Color(28, 30, 38);
    private static final Color BG2    = new Color(36, 39, 50);
    private static final Color ACCENT = new Color(72, 187, 120);
    private static final Color FG     = new Color(220, 223, 235);
    private static final Color FG_DIM = new Color(130, 135, 160);
    private static final Font  MONO   = new Font("Monospaced", Font.PLAIN, 12);

    public PrincipalSrv() {
        setTitle("Nodo Servidor");
        setSize(440, 370);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        aplicarEstilo();
        construirUI();
    }

    private static long obtenerPID() {
        try { return ProcessHandle.current().pid(); }
        catch (NoClassDefFoundError | UnsupportedOperationException e) {
            try { return Long.parseLong(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]); }
            catch (NumberFormatException ex) { return -1; }
        }
    }

    private void aplicarEstilo() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        getContentPane().setBackground(BG);
    }

    private void construirUI() {
        setLayout(new BorderLayout(0, 0));

        btnIniciar.setBackground(new Color(56, 161, 105));
        btnIniciar.setForeground(Color.WHITE);
        btnIniciar.setFocusPainted(false);
        btnIniciar.setBorderPainted(false);
        btnIniciar.setFont(btnIniciar.getFont().deriveFont(Font.BOLD, 12f));
        btnIniciar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnIniciar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        btnIniciar.addActionListener(e -> encenderServidor());

        JLabel lblTitulo = new JLabel("  NODO SERVIDOR");
        lblTitulo.setForeground(ACCENT);
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 13f));
        lblTitulo.setBackground(BG2);
        lblTitulo.setOpaque(true);
        lblTitulo.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(BG2);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ACCENT));
        top.add(lblTitulo,  BorderLayout.WEST);
        top.add(btnIniciar, BorderLayout.EAST);

        logArea.setEditable(false);
        logArea.setBackground(BG);
        logArea.setForeground(FG);
        logArea.setFont(MONO);
        logArea.setCaretColor(ACCENT);
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JLabel lblLog = new JLabel("  LOG");
        lblLog.setForeground(FG_DIM);
        lblLog.setFont(lblLog.getFont().deriveFont(Font.BOLD, 11f));
        lblLog.setBackground(BG2);
        lblLog.setOpaque(true);
        lblLog.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(55, 60, 80)));

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(BG);
        center.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        center.add(lblLog, BorderLayout.NORTH);
        center.add(scroll,  BorderLayout.CENTER);

        add(top,    BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Arranque
    // ─────────────────────────────────────────────────────────────────────────

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
                setTitle("Nodo Servidor :" + p + "  (PID " + MI_PID + ")");
                new Thread(() -> {
                    try {
                        while (true) new Thread(new Manejador(ss.accept())).start();
                    } catch (Exception e) { log("[!] Socket maestro: " + e.getMessage()); }
                }).start();
                break;
            } catch (IOException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registro
    // ─────────────────────────────────────────────────────────────────────────

    private void intentarRegistro(int intento) {
        if (registrado) return;
        if (intento > 3) {
            log("[!] LB no detectado. Reintentando en 10s...");
            reprogramarRegistro(1, 10000);
            return;
        }
        try (Socket s = new Socket("localhost", 12345);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("REGISTRO_SRV");
            out.println(miPuerto);
            out.println(MI_PID);
            log("[OK] Registrado en LB — :" + miPuerto + " PID " + MI_PID);
            registrado = true;
            iniciarMonitoreoLB();
        } catch (Exception e) {
            log("[!] LB fuera de línea. Intento " + intento + "/3...");
            reprogramarRegistro(intento + 1, 3000);
        }
    }

    private void iniciarMonitoreoLB() {
        new Thread(() -> {
            while (registrado) {
                try {
                    Thread.sleep(15000);
                    try (Socket test = new Socket("localhost", 12345)) { }
                } catch (Exception e) {
                    log("[ALERTA] LB perdido. Recuperando...");
                    registrado = false;
                    intentarRegistro(1);
                }
            }
        }).start();
    }

    private void reprogramarRegistro(int intento, int delay) {
        new Thread(() -> {
            try { Thread.sleep(delay); intentarRegistro(intento); }
            catch (Exception ignored) {}
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lista global de clientes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 1. Empuja la lista local al LB (PUSH_LISTA).
     * 2. Pide la lista global al LB (GET_LISTA_GLOBAL).
     * 3. La distribuye a todos los clientes locales como "LISTA:<global>".
     *
     * Llamar siempre dentro de synchronized(clientesLocales).
     */
    private void sincronizarUsuariosGlobales() {
        // Paso 1: empujar lista local al LB
        String listaLocal = String.join(",", clientesLocales.keySet());
        try (Socket s = new Socket("localhost", 12345);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("PUSH_LISTA");
            out.println(miPuerto);
            out.println(listaLocal);
        } catch (Exception ignored) {}

        // Paso 2: pedir lista global
        String listaGlobal = listaLocal; // fallback: al menos los locales
        try (Socket s = new Socket("localhost", 12345);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("GET_LISTA_GLOBAL");
            out.println(miPuerto);
            String resp = in.readLine();
            if (resp != null && !resp.isBlank()) listaGlobal = resp;
        } catch (Exception ignored) {}

        // Paso 3: enviar a todos los clientes locales
        // El cliente recibe "LISTA:nombre1@puerto1,nombre2@puerto2,..."
        // y filtra su propio nombre antes de mostrar
        final String trama = "LISTA:" + listaGlobal;
        clientesLocales.values().forEach(pw -> pw.println(trama));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manejador de conexiones
    // ─────────────────────────────────────────────────────────────────────────

    private class Manejador implements Runnable {
        private final Socket s;
        Manejador(Socket s) { this.s = s; }

        public void run() {
            String nombre = null;
            PrintWriter out = null;
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out = new PrintWriter(s.getOutputStream(), true);

                nombre = in.readLine();
                if (nombre == null) return;

                if (nombre.startsWith("INTERNAL:")) {
                    procesarInterno(nombre.substring(9));
                    return;
                }

                synchronized (clientesLocales) {
                    clientesLocales.put(nombre, out);
                    sincronizarUsuariosGlobales();
                }
                log("[+] Cliente: " + nombre);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("@")) enviarPrivado(nombre, line);
                    else broadcast(nombre + ": " + line, true);
                }

            } catch (Exception e) {
                log("[-] Desconexión: " + (nombre != null ? nombre : "?"));
            } finally {
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

    // ─────────────────────────────────────────────────────────────────────────
    // Mensajería
    // ─────────────────────────────────────────────────────────────────────────

    private void enviarPrivado(String de, String trama) {
        try {
            int esp = trama.indexOf(" ");
            if (esp == -1) return;
            // El destino puede venir como "nombre" o "nombre@puerto"
            String paraRaw = trama.substring(1, esp);
            String para    = paraRaw.contains("@") ? paraRaw.split("@")[0] : paraRaw;
            String msg     = trama.substring(esp + 1);
            if (clientesLocales.containsKey(para)) {
                clientesLocales.get(para).println("[P] " + de + ": " + msg);
            } else {
                enviarAMesh("INTERNAL:PV#" + de + "#" + para + "#" + msg);
            }
        } catch (Exception e) { log("[!] Error privado: " + e.getMessage()); }
    }

    private void broadcast(String m, boolean replica) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(m + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        clientesLocales.values().forEach(p -> p.println(m));
        if (replica) enviarAMesh("INTERNAL:BC#" + m);
    }

    private void enviarAMesh(String trama) {
        try (Socket s = new Socket("localhost", 12345);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println("GET_NODOS");
            String resp = in.readLine();
            if (resp == null) return;
            String lista = resp.replace("[","").replace("]","").replace(" ","");
            for (String pStr : lista.split(",")) {
                if (pStr.isEmpty()) continue;
                int p = Integer.parseInt(pStr);
                if (p == miPuerto) continue;
                try (Socket s2 = new Socket("localhost", p);
                     PrintWriter out2 = new PrintWriter(s2.getOutputStream(), true)) {
                    out2.println(trama);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void procesarInterno(String t) {
        String[] p = t.split("#");
        if (p[0].equals("BC")) broadcast(p[1], false);
        else if (p[0].equals("PV") && clientesLocales.containsKey(p[2]))
            clientesLocales.get(p[2]).println("[P] " + p[1] + ": " + p[3]);
    }

    private void log(String m) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(m + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}