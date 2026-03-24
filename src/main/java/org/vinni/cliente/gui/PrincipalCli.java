package org.vinni.cliente.gui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class PrincipalCli extends javax.swing.JFrame {
    private final int PORT = 12345;
    private Socket socket;
    private PrintWriter out;
    private String nombreUsuario;
    private DefaultListModel<String> modeloLista = new DefaultListModel<>();

    public PrincipalCli() {
        initComponents();
        getContentPane().setBackground(new Color(245, 245, 245));
    }

    private void initComponents() {
        this.setTitle("Chat Cliente");
        bConectar = new JButton("Conectar");
        bLimpiar = new JButton("Limpiar");
        btEnviar = new JButton("Enviar");
        mensajeTxt = new JTextField();
        mensajesTxt = new JTextArea();
        listaUsuariosUI = new JList<>(modeloLista);

        JScrollPane scMsg = new JScrollPane(mensajesTxt);
        JScrollPane scLis = new JScrollPane(listaUsuariosUI);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bConectar.setBounds(20, 15, 100, 30);
        bConectar.addActionListener(e -> conectarConReintentos());
        getContentPane().add(bConectar);

        bLimpiar.setBounds(130, 15, 100, 30);
        bLimpiar.addActionListener(e -> mensajesTxt.setText(""));
        getContentPane().add(bLimpiar);

        scMsg.setBounds(20, 55, 300, 150);
        mensajesTxt.setEditable(false);
        getContentPane().add(scMsg);

        scLis.setBounds(330, 55, 110, 150);
        getContentPane().add(scLis);

        mensajeTxt.setBounds(20, 215, 210, 30);
        getContentPane().add(mensajeTxt);

        btEnviar.setBounds(240, 215, 80, 30);
        btEnviar.addActionListener(e -> enviarMensaje());
        getContentPane().add(btEnviar);

        modeloLista.addElement("Todos");
        setSize(470, 300);
        setLocationRelativeTo(null);
    }

    private void conectarConReintentos() {
        if (nombreUsuario == null) nombreUsuario = JOptionPane.showInputDialog("Nombre:");

        new Thread(() -> {
            int intentos = 0;
            while (intentos < 3) {
                try {
                    log("[INFO] Conectando...");
                    socket = new Socket("localhost", PORT);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(nombreUsuario);
                    bConectar.setEnabled(false);
                    escuchar();
                    return;
                } catch (IOException e) {
                    intentos++;
                    log("[FALLO] Reintento " + intentos + " en 2s...");
                    try { Thread.sleep(2000); } catch (Exception ex) {}
                }
            }
            log("[ERROR] Servidor no responde.");
        }).start();
    }

    private void escuchar() {
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String s;
                while ((s = in.readLine()) != null) {
                    if (s.startsWith("LISTA:")) actualizarLista(s.substring(6));
                    else mensajesTxt.append(s + "\n");
                }
            } catch (IOException e) {
                log("[SISTEMA] Conexión cerrada.");
            } finally {
                limpiarInterfaz();
                conectarConReintentos(); // Política de reconexión tras caída
            }
        }).start();
    }

    private void limpiarInterfaz() {
        out = null;
        bConectar.setEnabled(true);
        SwingUtilities.invokeLater(() -> {
            modeloLista.clear();
            modeloLista.addElement("Todos");
        });
    }

    private void log(String m) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(m + "\n"));
    }

    private void enviarMensaje() {
        if (out == null) return;
        String dest = listaUsuariosUI.getSelectedValue();
        String m = mensajeTxt.getText();
        if (m.isEmpty()) return;

        if (dest == null || dest.equals("Todos")) out.println(m);
        else {
            out.println("@" + dest + " " + m);
            mensajesTxt.append("[Privado para " + dest + "]: " + m + "\n");
        }
        mensajeTxt.setText("");
    }

    private void actualizarLista(String datos) {
        SwingUtilities.invokeLater(() -> {
            modeloLista.clear();
            modeloLista.addElement("Todos");
            for (String u : datos.split(",")) if(!u.equals(nombreUsuario)) modeloLista.addElement(u);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
    private JButton bConectar, bLimpiar, btEnviar;
    private JTextField mensajeTxt;
    private JTextArea mensajesTxt;
    private JList<String> listaUsuariosUI;
}