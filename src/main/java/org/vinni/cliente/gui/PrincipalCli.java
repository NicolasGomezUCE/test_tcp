package org.vinni.cliente.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class PrincipalCli extends JFrame {

    private Socket     socket;
    private PrintWriter out;
    private String     nombre;

    // ── Control de reconexión ─────────────────────────────────────────────────
    private volatile boolean reconectandose  = false;
    private volatile int     intentosReconexion = 0;
    private static final int MAX_RECONEXIONES  = 3;

    // ── Modelo de la lista de usuarios ────────────────────────────────────────
    // Cada entrada: "nombre@puerto" — se muestra con renderer personalizado
    private final DefaultListModel<String> modeloUsuarios = new DefaultListModel<>();
    private final JList<String>            listaUsuarios  = new JList<>(modeloUsuarios);

    // ── UI ────────────────────────────────────────────────────────────────────
    private final JTextArea  areaChat = new JTextArea();
    private final JTextField campoMsg = new JTextField();
    private final JButton    btnCon   = new JButton("Conectar");
    private final JLabel     lblEstado = new JLabel("● Desconectado");

    // ── Paleta oscura ─────────────────────────────────────────────────────────
    private static final Color BG        = new Color(24,  26,  32);
    private static final Color BG2       = new Color(32,  34,  44);
    private static final Color BG3       = new Color(40,  44,  58);
    private static final Color ACCENT    = new Color(99, 179, 237);
    private static final Color GREEN     = new Color(72, 187, 120);
    private static final Color RED       = new Color(252, 129, 129);
    private static final Color YELLOW    = new Color(246, 173,  85);
    private static final Color FG        = new Color(220, 223, 235);
    private static final Color FG_DIM    = new Color(120, 126, 155);
    private static final Color BUBBLE_ME = new Color(49,  86, 155);
    private static final Color BUBBLE_OT = new Color(44,  47,  62);
    private static final Font  MONO      = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font  SANS      = new Font("SansSerif",  Font.PLAIN, 12);

    public PrincipalCli() {
        setTitle("Chat Cliente");
        setSize(700, 500);
        setMinimumSize(new Dimension(560, 380));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        aplicarEstilo();
        construirUI();
    }

    private void aplicarEstilo() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        getContentPane().setBackground(BG);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    private void construirUI() {
        setLayout(new BorderLayout(0, 0));

        // ── Barra superior ────────────────────────────────────────────────
        btnCon.setBackground(ACCENT);
        btnCon.setForeground(BG);
        btnCon.setFocusPainted(false);
        btnCon.setBorderPainted(false);
        btnCon.setFont(btnCon.getFont().deriveFont(Font.BOLD, 12f));
        btnCon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCon.setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 18));
        btnCon.addActionListener(e -> iniciarConexionManual());

        lblEstado.setForeground(RED);
        lblEstado.setFont(SANS.deriveFont(Font.BOLD, 12f));

        JLabel lblTitulo = new JLabel("  CHAT DISTRIBUIDO");
        lblTitulo.setForeground(ACCENT);
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 13f));

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setBackground(BG2);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 60, 80)),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        top.add(lblTitulo, BorderLayout.WEST);
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topRight.setBackground(BG2);
        topRight.add(lblEstado);
        topRight.add(btnCon);
        top.add(topRight, BorderLayout.EAST);

        // ── Lista lateral de usuarios ─────────────────────────────────────
        listaUsuarios.setBackground(BG2);
        listaUsuarios.setForeground(FG);
        listaUsuarios.setFont(SANS);
        listaUsuarios.setSelectionBackground(new Color(49, 70, 130));
        listaUsuarios.setSelectionForeground(Color.WHITE);
        listaUsuarios.setFixedCellHeight(36);
        listaUsuarios.setCellRenderer(new UsuarioRenderer());
        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaUsuarios.setToolTipText("Selecciona un usuario para mensaje privado");

        // Doble click → deseleccionar (volver a broadcast)
        listaUsuarios.addMouseListener(new MouseAdapter() {
            private int lastIndex = -1;
            public void mouseClicked(MouseEvent e) {
                int idx = listaUsuarios.locationToIndex(e.getPoint());
                if (idx == lastIndex) { listaUsuarios.clearSelection(); lastIndex = -1; }
                else lastIndex = idx;
            }
        });

        JLabel lblUsuarios = new JLabel("  EN LÍNEA");
        lblUsuarios.setForeground(FG_DIM);
        lblUsuarios.setFont(lblUsuarios.getFont().deriveFont(Font.BOLD, 11f));
        lblUsuarios.setBackground(BG2);
        lblUsuarios.setOpaque(true);
        lblUsuarios.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        scrollUsuarios.getViewport().setBackground(BG2);
        scrollUsuarios.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(55, 60, 80)));

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG2);
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.add(lblUsuarios,    BorderLayout.NORTH);
        sidebar.add(scrollUsuarios, BorderLayout.CENTER);

        // Hint bajo la lista
        JLabel hint = new JLabel("<html><center><small>Clic para privado<br>Doble clic para deselec.</small></center></html>");
        hint.setForeground(FG_DIM);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 4, 6, 4));
        hint.setBackground(BG2);
        hint.setOpaque(true);
        sidebar.add(hint, BorderLayout.SOUTH);

        // ── Área de chat ──────────────────────────────────────────────────
        areaChat.setEditable(false);
        areaChat.setBackground(BG);
        areaChat.setForeground(FG);
        areaChat.setFont(MONO);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.setCaretColor(ACCENT);
        areaChat.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scrollChat = new JScrollPane(areaChat);
        scrollChat.getViewport().setBackground(BG);
        scrollChat.setBorder(null);

        // ── Barra de envío ────────────────────────────────────────────────
        campoMsg.setBackground(BG3);
        campoMsg.setForeground(FG);
        campoMsg.setCaretColor(ACCENT);
        campoMsg.setFont(SANS.deriveFont(13f));
        campoMsg.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 90)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        campoMsg.addActionListener(e -> enviar());

        JButton btnEnviar = new JButton("Enviar ▶");
        btnEnviar.setBackground(ACCENT);
        btnEnviar.setForeground(BG);
        btnEnviar.setFocusPainted(false);
        btnEnviar.setBorderPainted(false);
        btnEnviar.setFont(btnEnviar.getFont().deriveFont(Font.BOLD, 12f));
        btnEnviar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnEnviar.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        btnEnviar.addActionListener(e -> enviar());

        // Label de destino activo
        JLabel lblDestino = new JLabel("Modo: broadcast");
        lblDestino.setForeground(FG_DIM);
        lblDestino.setFont(SANS.deriveFont(11f));
        listaUsuarios.addListSelectionListener(e -> {
            String sel = listaUsuarios.getSelectedValue();
            if (sel != null) {
                String nombre = sel.contains("@") ? sel.split("@")[0] : sel;
                lblDestino.setForeground(YELLOW);
                lblDestino.setText("Privado → " + nombre);
            } else {
                lblDestino.setForeground(FG_DIM);
                lblDestino.setText("Modo: broadcast");
            }
        });

        JPanel barraEnvio = new JPanel(new BorderLayout(6, 0));
        barraEnvio.setBackground(BG2);
        barraEnvio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(55, 60, 80)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JPanel barraIzq = new JPanel(new BorderLayout(4, 2));
        barraIzq.setBackground(BG2);
        barraIzq.add(lblDestino, BorderLayout.NORTH);
        barraIzq.add(campoMsg,   BorderLayout.CENTER);
        barraEnvio.add(barraIzq,  BorderLayout.CENTER);
        barraEnvio.add(btnEnviar, BorderLayout.EAST);

        // ── Ensamblado ────────────────────────────────────────────────────
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(BG);
        chatPanel.add(scrollChat,  BorderLayout.CENTER);
        chatPanel.add(barraEnvio,  BorderLayout.SOUTH);

        add(top,       BorderLayout.NORTH);
        add(sidebar,   BorderLayout.WEST);
        add(chatPanel, BorderLayout.CENTER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Renderer personalizado para la lista de usuarios
    // ─────────────────────────────────────────────────────────────────────────

    private class UsuarioRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            String raw    = value.toString();
            String nomUsr = raw.contains("@") ? raw.split("@")[0] : raw;
            String nodo   = raw.contains("@") ? raw.split("@")[1] : "";

            JPanel panel = new JPanel(new BorderLayout(6, 0));
            panel.setBackground(isSelected ? new Color(49, 70, 130) : BG2);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 8));

            // Círculo de estado
            JLabel dot = new JLabel("●");
            dot.setForeground(GREEN);
            dot.setFont(dot.getFont().deriveFont(10f));

            // Nombre
            JLabel lblNom = new JLabel(nomUsr);
            lblNom.setForeground(isSelected ? Color.WHITE : FG);
            lblNom.setFont(SANS.deriveFont(Font.BOLD, 12f));

            // Badge del nodo
            JLabel badge = new JLabel(":" + nodo);
            badge.setForeground(FG_DIM);
            badge.setFont(SANS.deriveFont(10f));
            badge.setHorizontalAlignment(SwingConstants.RIGHT);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            left.setBackground(panel.getBackground());
            left.add(dot);
            left.add(lblNom);

            panel.add(left,  BorderLayout.CENTER);
            panel.add(badge, BorderLayout.EAST);
            return panel;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conexión
    // ─────────────────────────────────────────────────────────────────────────

    private void iniciarConexionManual() {
        intentosReconexion = 0;
        reconectandose     = false;
        btnCon.setEnabled(false);
        conectar();
    }

    private void conectar() {
        if (nombre == null) {
            nombre = JOptionPane.showInputDialog(this, "Tu nombre en el chat:", "Conectar", JOptionPane.PLAIN_MESSAGE);
        }
        if (nombre == null || nombre.isBlank()) {
            nombre = null;
            SwingUtilities.invokeLater(() -> btnCon.setEnabled(true));
            return;
        }

        new Thread(() -> {
            try {
                if (socket != null && !socket.isClosed())
                    try { socket.close(); } catch (IOException ignored) {}

                socket = new Socket("localhost", 12345);
                out    = new PrintWriter(socket.getOutputStream(), true);
                out.println(nombre);

                intentosReconexion = 0;
                reconectandose     = false;

                SwingUtilities.invokeLater(() -> {
                    setTitle("Chat — " + nombre);
                    lblEstado.setText("● Conectado");
                    lblEstado.setForeground(GREEN);
                    btnCon.setEnabled(false);
                    appendSistema("Conectado como " + nombre);
                });

                escuchar(new BufferedReader(new InputStreamReader(socket.getInputStream())));

            } catch (Exception e) {
                manejarFallo("[!] No se pudo conectar");
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Escucha
    // ─────────────────────────────────────────────────────────────────────────

    private void escuchar(BufferedReader in) {
        try {
            String linea;
            while ((linea = in.readLine()) != null) {
                final String l = linea;
                if (l.contains("No hay servidores disponibles")) {
                    SwingUtilities.invokeLater(() -> appendSistema(l));
                    continue;
                }
                if (l.startsWith("LISTA:")) {
                    actualizarLista(l.substring(6));
                } else if (l.startsWith("[P] ")) {
                    SwingUtilities.invokeLater(() -> appendPrivado(l.substring(4)));
                } else if (l.startsWith("[SISTEMA]") || l.startsWith("[!]") || l.startsWith("[INFO]")) {
                    SwingUtilities.invokeLater(() -> appendSistema(l));
                } else {
                    SwingUtilities.invokeLater(() -> appendMensaje(l));
                }
            }
            SwingUtilities.invokeLater(() -> appendSistema("Conexión cerrada por el servidor."));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> appendSistema("Conexión interrumpida."));
        } finally {
            SwingUtilities.invokeLater(() -> {
                lblEstado.setText("● Desconectado");
                lblEstado.setForeground(RED);
                modeloUsuarios.clear();
            });
            manejarFallo(null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reconexión
    // ─────────────────────────────────────────────────────────────────────────

    private void manejarFallo(String mensaje) {
        if (mensaje != null) SwingUtilities.invokeLater(() -> appendSistema(mensaje));

        synchronized (this) {
            if (reconectandose) return;
            reconectandose = true;
        }
        intentosReconexion++;

        if (intentosReconexion > MAX_RECONEXIONES) {
            reconectandose = false;
            SwingUtilities.invokeLater(() -> {
                appendSistema("Sin conexión tras " + MAX_RECONEXIONES + " intentos. Pulsa 'Conectar'.");
                btnCon.setEnabled(true);
            });
            return;
        }

        final int intento = intentosReconexion;
        SwingUtilities.invokeLater(() ->
                appendSistema("Reconectando... (" + intento + "/" + MAX_RECONEXIONES + ") en 3s"));

        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); return;
            }
            reconectandose = false;
            conectar();
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Envío
    // ─────────────────────────────────────────────────────────────────────────

    private void enviar() {
        String m = campoMsg.getText().trim();
        if (m.isEmpty() || out == null) return;
        try {
            String sel = listaUsuarios.getSelectedValue();
            if (sel != null) {
                // Enviar como "@nombre mensaje" — el servidor extrae el nombre
                String destNombre = sel.contains("@") ? sel.split("@")[0] : sel;
                out.println("@" + destNombre + " " + m);
                SwingUtilities.invokeLater(() -> appendPrivadoPropio(destNombre, m));
            } else {
                out.println(m);
            }
            if (out.checkError()) throw new IOException("Error de escritura");
            SwingUtilities.invokeLater(() -> campoMsg.setText(""));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> appendSistema("Error al enviar. Reconectando..."));
            manejarFallo(null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lista global de usuarios
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recibe "nombre1@puerto1,nombre2@puerto1,nombre3@puerto2,..."
     * Muestra todos excepto el propio cliente.
     */
    private void actualizarLista(String data) {
        SwingUtilities.invokeLater(() -> {
            modeloUsuarios.clear();
            if (data == null || data.isBlank()) return;
            for (String entrada : data.split(",")) {
                entrada = entrada.trim();
                if (entrada.isBlank()) continue;
                // Filtrar el propio nombre (con o sin @nodo)
                String nomEntrada = entrada.contains("@") ? entrada.split("@")[0] : entrada;
                if (!nomEntrada.equals(nombre)) {
                    modeloUsuarios.addElement(entrada);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de UI para distintos tipos de mensaje
    // ─────────────────────────────────────────────────────────────────────────

    private void appendMensaje(String texto) {
        areaChat.append(texto + "\n");
        scrollDown();
    }

    private void appendPrivado(String texto) {
        // "[P] remitente: mensaje" ya viene sin el prefijo [P] aquí
        areaChat.append("🔒 " + texto + "\n");
        scrollDown();
    }

    private void appendPrivadoPropio(String dest, String msg) {
        areaChat.append("🔒 [Privado → " + dest + "]: " + msg + "\n");
        scrollDown();
    }

    private void appendSistema(String texto) {
        // Limpiar prefijos redundantes antes de mostrar
        String limpio = texto.replaceAll("^\\[SISTEMA\\]\\s*", "")
                .replaceAll("^\\[!\\]\\s*", "")
                .replaceAll("^\\[INFO\\]\\s*", "");
        areaChat.append("─── " + limpio + " ───\n");
        scrollDown();
    }

    private void scrollDown() {
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
}