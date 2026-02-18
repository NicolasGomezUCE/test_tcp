package org.vinni.cliente.gui;

import javax.swing.*;
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
    }

    private void initComponents() {
        this.setTitle("Cliente Chat");
        bConectar = new JButton("Conectar");
        btEnviar = new JButton("Enviar");
        mensajeTxt = new JTextField();
        mensajesTxt = new JTextArea();
        listaUsuariosUI = new JList<>(modeloLista);
        JScrollPane scMensajes = new JScrollPane(mensajesTxt);
        JScrollPane scLista = new JScrollPane(listaUsuariosUI);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bConectar.addActionListener(e -> conectar());
        getContentPane().add(bConectar); bConectar.setBounds(20, 10, 100, 30);

        getContentPane().add(scMensajes); scMensajes.setBounds(20, 50, 300, 150);
        getContentPane().add(scLista); scLista.setBounds(330, 50, 100, 150);

        getContentPane().add(mensajeTxt); mensajeTxt.setBounds(20, 210, 300, 30);
        btEnviar.addActionListener(e -> enviarMensaje());
        getContentPane().add(btEnviar); btEnviar.setBounds(330, 210, 100, 30);

        modeloLista.addElement("Todos");
        setSize(460, 300);
        setLocationRelativeTo(null);
    }

    private void conectar() {
        nombreUsuario = JOptionPane.showInputDialog("Tu nombre:");
        try {
            socket = new Socket("localhost", PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(nombreUsuario); // Registro

            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String s;
                    while ((s = in.readLine()) != null) {
                        if (s.startsWith("LISTA:")) {
                            actualizarLista(s.substring(6));
                        } else {
                            mensajesTxt.append(s + "\n");
                        }
                    }
                } catch (IOException e) { }
            }).start();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void actualizarLista(String datos) {
        SwingUtilities.invokeLater(() -> {
            modeloLista.clear();
            modeloLista.addElement("Todos");
            for (String u : datos.split(",")) {
                if (!u.isEmpty() && !u.equals(nombreUsuario)) modeloLista.addElement(u);
            }
        });
    }

    private void enviarMensaje() {
        String destino = listaUsuariosUI.getSelectedValue();
        String msg = mensajeTxt.getText();
        if (destino == null || destino.equals("Todos")) {
            out.println(msg);
        } else {
            out.println("@" + destino + " " + msg);
            mensajesTxt.append("[Privado para " + destino + "]: " + msg + "\n");
        }
        mensajeTxt.setText("");
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalCli().setVisible(true));
    }

    private JButton bConectar, btEnviar;
    private JTextField mensajeTxt;
    private JTextArea mensajesTxt;
    private JList<String> listaUsuariosUI;
}