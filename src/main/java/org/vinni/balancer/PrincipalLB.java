package org.vinni.balancer;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PrincipalLB extends JFrame {

    // ── Estado ────────────────────────────────────────────────────────────────
    private ServerSocket lbSocket;
    private final List<Integer>         nodosActivos    = new CopyOnWriteArrayList<>();
    private final AtomicInteger         turno           = new AtomicInteger(0);
    private boolean                     encendido       = false;

    // puerto → lista de nombres de clientes conectados en ese nodo
    // Actualizado por PUSH_LISTA; consultado por GET_LISTA_GLOBAL
    private final Map<Integer, List<String>> clientesPorNodo = new ConcurrentHashMap<>();

    // PID y procesos de nodos lanzados desde aquí
    private final Map<Integer, Long>    pidPorPuerto     = new ConcurrentHashMap<>();
    private final Map<Integer, Process> procesoPorPuerto = new ConcurrentHashMap<>();

    // ── UI ────────────────────────────────────────────────────────────────────
    private final JTextArea            logArea     = new JTextArea();
    private final DefaultListModel<String> modeloNodos = new DefaultListModel<>();
    private final JList<String>        listaNodos  = new JList<>(modeloNodos);
    private JButton                    btnLanzarNodo;
    private JButton                    btnApagarNodo;

    private String classpathServidor = "";
    private static final String CLASE_SERVIDOR = "org.vinni.servidor.gui.PrincipalSrv";

    // ── Paleta oscura ─────────────────────────────────────────────────────────
    private static final Color BG       = new Color(28,  30,  38);
    private static final Color BG2      = new Color(36,  39,  50);
    private static final Color ACCENT   = new Color(99, 179, 237);
    private static final Color FG       = new Color(220, 223, 235);
    private static final Color FG_DIM   = new Color(130, 135, 160);
    private static final Font  MONO     = new Font("Monospaced", Font.PLAIN, 12);

    public PrincipalLB() {
        setTitle("Balanceador — Proxy Activo");
        setSize(680, 460);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        aplicarLookAndFeel();
        construirUI();
        autoDetectarClasspath();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Look & Feel
    // ─────────────────────────────────────────────────────────────────────────

    private void aplicarLookAndFeel() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        getContentPane().setBackground(BG);
    }

    private JButton boton(String texto, Color bg) {
        JButton b = new JButton(texto);
        b.setBackground(bg);
        b.setForeground(FG);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JPanel panelOscuro(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(BG2);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    private void construirUI() {
        setLayout(new BorderLayout(6, 6));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── Barra superior ────────────────────────────────────────────────
        JButton btnOn  = boton("▶  Encender", new Color(56, 161, 105));
        JButton btnOff = boton("■  Apagar",   new Color(197, 48, 48));
        JLabel lblTitulo = new JLabel("  BALANCEADOR CENTRAL");
        lblTitulo.setForeground(ACCENT);
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 13f));

        JPanel top = panelOscuro(new BorderLayout(8, 0));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ACCENT),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JPanel topBtns = panelOscuro(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topBtns.add(btnOn); topBtns.add(btnOff);
        top.add(lblTitulo, BorderLayout.WEST);
        top.add(topBtns,   BorderLayout.EAST);
        btnOn.addActionListener(e  -> iniciar());
        btnOff.addActionListener(e -> apagar());

        // ── Panel lateral de nodos ────────────────────────────────────────
        btnLanzarNodo = boton("＋  Lanzar nodo",    new Color(49, 130, 206));
        btnApagarNodo = boton("✕  Apagar nodo",     new Color(160, 50, 50));
        JButton btnCfg = boton("⚙  Ruta classpath", new Color(60, 60, 80));
        btnLanzarNodo.setEnabled(false);
        btnApagarNodo.setEnabled(false);
        btnLanzarNodo.addActionListener(e -> lanzarServidorExterno());
        btnApagarNodo.addActionListener(e -> apagarNodoSeleccionado());
        btnCfg.addActionListener(e        -> configurarClasspath());

        listaNodos.setBackground(BG);
        listaNodos.setForeground(FG);
        listaNodos.setFont(MONO);
        listaNodos.setSelectionBackground(new Color(49, 70, 120));
        listaNodos.setSelectionForeground(Color.WHITE);
        listaNodos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaNodos.addListSelectionListener(e ->
                btnApagarNodo.setEnabled(!listaNodos.isSelectionEmpty()));

        JLabel lblNodos = new JLabel("  NODOS ACTIVOS");
        lblNodos.setForeground(FG_DIM);
        lblNodos.setFont(lblNodos.getFont().deriveFont(Font.BOLD, 11f));

        JPanel sidebar = panelOscuro(new BorderLayout(4, 6));
        sidebar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        sidebar.setPreferredSize(new Dimension(210, 0));

        JPanel sideButtons = panelOscuro(new GridLayout(3, 1, 0, 4));
        sideButtons.add(btnLanzarNodo);
        sideButtons.add(btnApagarNodo);
        sideButtons.add(btnCfg);

        JScrollPane scrollNodos = new JScrollPane(listaNodos);
        scrollNodos.getViewport().setBackground(BG);
        scrollNodos.setBorder(BorderFactory.createLineBorder(new Color(55, 60, 80)));

        sidebar.add(lblNodos,    BorderLayout.NORTH);
        sidebar.add(scrollNodos, BorderLayout.CENTER);
        sidebar.add(sideButtons, BorderLayout.SOUTH);

        // ── Log ───────────────────────────────────────────────────────────
        logArea.setEditable(false);
        logArea.setBackground(BG);
        logArea.setForeground(FG);
        logArea.setFont(MONO);
        logArea.setCaretColor(ACCENT);
        logArea.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JLabel lblLog = new JLabel("  LOG");
        lblLog.setForeground(FG_DIM);
        lblLog.setFont(lblLog.getFont().deriveFont(Font.BOLD, 11f));
        lblLog.setBackground(BG2);
        lblLog.setOpaque(true);
        lblLog.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.getViewport().setBackground(BG);
        scrollLog.setBorder(BorderFactory.createLineBorder(new Color(55, 60, 80)));

        JPanel center = panelOscuro(new BorderLayout());
        center.add(lblLog,    BorderLayout.NORTH);
        center.add(scrollLog, BorderLayout.CENTER);

        add(top,     BorderLayout.NORTH);
        add(sidebar, BorderLayout.EAST);
        add(center,  BorderLayout.CENTER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Balanceador
    // ─────────────────────────────────────────────────────────────────────────

    private void iniciar() {
        if (encendido) return;
        encendido = true;
        btnLanzarNodo.setEnabled(true);
        new Thread(() -> {
            try {
                lbSocket = new ServerSocket(12345);
                log("▶ Balanceador escuchando en :12345");
                while (encendido) {
                    Socket s = lbSocket.accept();
                    new Thread(() -> gestionarPeticion(s)).start();
                }
            } catch (IOException e) {
                if (encendido) log("[ERROR] " + e.getMessage());
            }
        }).start();
    }

    private void apagar() {
        encendido = false;
        btnLanzarNodo.setEnabled(false);
        btnApagarNodo.setEnabled(false);
        try { if (lbSocket != null) lbSocket.close(); } catch (IOException ignored) {}
        nodosActivos.clear();
        clientesPorNodo.clear();
        pidPorPuerto.clear();
        procesoPorPuerto.values().forEach(p -> { if (p.isAlive()) p.destroy(); });
        procesoPorPuerto.clear();
        SwingUtilities.invokeLater(modeloNodos::clear);
        log("■ Balanceador apagado.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Protocolo
    // ─────────────────────────────────────────────────────────────────────────

    private void gestionarPeticion(Socket cliente) {
        try {
            String protocolo = leerLineaRaw(cliente.getInputStream());
            if (protocolo == null) { cliente.close(); return; }

            switch (protocolo) {

                case "REGISTRO_SRV": {
                    String puertStr = leerLineaRaw(cliente.getInputStream());
                    String pidStr   = leerLineaRaw(cliente.getInputStream());
                    if (puertStr == null) { cliente.close(); return; }
                    int puerto = Integer.parseInt(puertStr.trim());
                    long pid = -1;
                    try { if (pidStr != null) pid = Long.parseLong(pidStr.trim()); }
                    catch (NumberFormatException ignored) {}

                    if (!nodosActivos.contains(puerto)) {
                        nodosActivos.add(puerto);
                        clientesPorNodo.put(puerto, new CopyOnWriteArrayList<>());
                        pidPorPuerto.put(puerto, pid);
                        final long pidF = pid;
                        SwingUtilities.invokeLater(() ->
                                modeloNodos.addElement(etiquetaNodo(puerto, pidF)));
                        log("[NODO +] :" + puerto + "  PID " + pid);
                    }
                    cliente.close();
                    break;
                }

                case "GET_NODOS": {
                    new PrintWriter(cliente.getOutputStream(), true)
                            .println(nodosActivos.toString());
                    cliente.close();
                    break;
                }

                // ── NUEVO: el servidor empuja su lista de clientes locales ──
                // Formato: PUSH_LISTA\n<puerto>\n<nombre1,nombre2,...>
                case "PUSH_LISTA": {
                    String puertStr  = leerLineaRaw(cliente.getInputStream());
                    String listaStr  = leerLineaRaw(cliente.getInputStream());
                    if (puertStr != null && listaStr != null) {
                        int puerto = Integer.parseInt(puertStr.trim());
                        List<String> nombres = new CopyOnWriteArrayList<>();
                        if (!listaStr.isBlank()) {
                            for (String n : listaStr.split(","))
                                if (!n.isBlank()) nombres.add(n.trim());
                        }
                        clientesPorNodo.put(puerto, nombres);
                    }
                    cliente.close();
                    break;
                }

                // ── NUEVO: cualquier nodo pide la lista global de clientes ──
                // Responde: <nombre@nodo,...> de todos los nodos
                case "GET_LISTA_GLOBAL": {
                    String puertPidStr = leerLineaRaw(cliente.getInputStream()); // puerto del que pide
                    PrintWriter pw = new PrintWriter(cliente.getOutputStream(), true);
                    pw.println(construirListaGlobal());
                    cliente.close();
                    break;
                }

                default: {
                    // Es el nombre de un cliente → redirigir
                    if (nodosActivos.isEmpty()) {
                        new PrintWriter(cliente.getOutputStream(), true)
                                .println("[SISTEMA] No hay servidores disponibles en este momento.");
                        log("[!] Cliente rechazado: sin nodos activos.");
                        cliente.close();
                        return;
                    }
                    redirigirConFailover(cliente, protocolo);
                    break;
                }
            }
        } catch (IOException e) {
            log("[ERROR] petición: " + e.getMessage());
        }
    }

    /** Construye "nombre1@puerto1,nombre2@puerto1,nombre3@puerto2,..." */
    private String construirListaGlobal() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, List<String>> e : clientesPorNodo.entrySet()) {
            for (String nombre : e.getValue()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(nombre).append("@").append(e.getKey());
            }
        }
        return sb.toString();
    }

    private String leerLineaRaw(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') continue;
            sb.append((char) b);
        }
        return (b == -1 && sb.length() == 0) ? null : sb.toString();
    }

    private void redirigirConFailover(Socket cliente, String nombre) {
        List<Integer> snapshot = new ArrayList<>(nodosActivos);
        for (int i = 0; i < snapshot.size(); i++) {
            int index = Math.abs(turno.getAndIncrement() % snapshot.size());
            int p = snapshot.get(index);
            try {
                Socket srv = new Socket("localhost", p);
                new PrintWriter(srv.getOutputStream(), true).println(nombre);
                Thread t1 = new Thread(() -> bridge(cliente, srv, p));
                Thread t2 = new Thread(() -> bridge(srv, cliente, p));
                t1.setDaemon(true); t2.setDaemon(true);
                t1.start(); t2.start();
                log("[→] " + nombre + " ▸ Nodo :" + p);
                return;
            } catch (IOException e) {
                retirarNodo(p, "[NODO ✕] :" + p + " caído.");
            }
        }
        try {
            new PrintWriter(cliente.getOutputStream(), true)
                    .println("[SISTEMA] No hay servidores disponibles en este momento.");
            cliente.close();
        } catch (IOException ignored) {}
        log("[!] Sin nodos disponibles para: " + nombre);
    }

    private void bridge(Socket entrada, Socket salida, int puertoNodo) {
        try (InputStream in = entrada.getInputStream();
             OutputStream out = salida.getOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); out.flush(); }
        } catch (IOException e) {
            if (!salida.isClosed() && salida.getPort() != 12345)
                retirarNodo(puertoNodo, "[NODO ✕] :" + puertoNodo + " caído en sesión.");
        } finally {
            try { entrada.close(); } catch (IOException ignored) {}
            try { salida.close(); } catch (IOException ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nodos
    // ─────────────────────────────────────────────────────────────────────────

    private void retirarNodo(int puerto, String msg) {
        nodosActivos.remove(Integer.valueOf(puerto));
        clientesPorNodo.remove(puerto);
        pidPorPuerto.remove(puerto);
        Process proc = procesoPorPuerto.remove(puerto);
        if (proc != null && proc.isAlive()) proc.destroy();
        SwingUtilities.invokeLater(() -> {
            for (int j = 0; j < modeloNodos.size(); j++) {
                if (modeloNodos.get(j).contains(":" + puerto)) { modeloNodos.remove(j); break; }
            }
        });
        if (msg != null) log(msg);
    }

    private void lanzarServidorExterno() {
        if (classpathServidor.isEmpty() && !configurarClasspath()) return;
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpathServidor, CLASE_SERVIDOR);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();
            log("[NODO] Proceso lanzado. Esperando registro...");
            new Thread(() -> {
                try { proceso.waitFor(); procesoPorPuerto.entrySet().stream()
                        .filter(e -> e.getValue() == proceso).map(Map.Entry::getKey)
                        .findFirst().ifPresent(p -> retirarNodo(p, "[NODO] Proceso :" + p + " terminó."));
                } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }).start();
            esperarYMapearProceso(proceso);
        } catch (IOException e) {
            log("[ERROR] No se pudo lanzar: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "No se pudo lanzar el proceso.\nVerifica java en PATH y el classpath.\n" + classpathServidor,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void esperarYMapearProceso(Process proceso) {
        new Thread(() -> {
            long pid = proceso.pid();
            for (int i = 0; i < 30; i++) {
                try { Thread.sleep(500); } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); return;
                }
                for (Map.Entry<Integer, Long> e : pidPorPuerto.entrySet()) {
                    if (e.getValue() == pid) {
                        procesoPorPuerto.put(e.getKey(), proceso);
                        log("[NODO] PID " + pid + " → nodo :" + e.getKey());
                        return;
                    }
                }
            }
            log("[WARN] No se mapeó PID " + pid + " tras 15s.");
        }).start();
    }

    private void apagarNodoSeleccionado() {
        String sel = listaNodos.getSelectedValue();
        if (sel == null) return;
        try {
            int puerto = Integer.parseInt(sel.replace("Nodo :", "").trim().split("\\s")[0]);
            retirarNodo(puerto, "[NODO] :" + puerto + " apagado manualmente.");
        } catch (NumberFormatException e) {
            log("[!] No se pudo determinar el puerto.");
        }
    }

    private String etiquetaNodo(int puerto, long pid) {
        return "Nodo :" + puerto + "  PID " + (pid > 0 ? pid : "?");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classpath
    // ─────────────────────────────────────────────────────────────────────────

    private void autoDetectarClasspath() {
        String cp = System.getProperty("java.class.path");
        classpathServidor = (cp != null && !cp.isBlank()) ? cp : System.getProperty("user.dir");
        log("[INFO] Classpath: " + classpathServidor);
    }

    private boolean configurarClasspath() {
        JTextField campo = new JTextField(classpathServidor, 42);
        JButton btnExp = new JButton("Explorar...");
        btnExp.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                campo.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("<html>Ruta al <b>.jar</b> o carpeta <b>classes</b> del servidor</html>"), BorderLayout.NORTH);
        panel.add(campo, BorderLayout.CENTER);
        panel.add(btnExp, BorderLayout.EAST);
        int res = JOptionPane.showConfirmDialog(this, panel, "Classpath del servidor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION && !campo.getText().isBlank()) {
            classpathServidor = campo.getText().trim();
            log("[CONFIG] Classpath: " + classpathServidor);
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void log(String m) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(m + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalLB().setVisible(true));
    }
}