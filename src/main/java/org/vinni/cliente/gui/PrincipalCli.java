package org.vinni.cliente.gui;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.Base64;

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
        this.setTitle("Cliente Multimedia");
        bConectar = new JButton("Conectar");
        btEnviar = new JButton("Enviar");
        btAdjuntar = new JButton("Adjuntar (1KB)");
        mensajeTxt = new JTextField();
        mensajesTxt = new JTextArea();
        listaUsuariosUI = new JList<>(modeloLista);

        JScrollPane scMsg = new JScrollPane(mensajesTxt);
        JScrollPane scLis = new JScrollPane(listaUsuariosUI);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bConectar.setBounds(20, 10, 100, 30);
        bConectar.addActionListener(e -> conectar());
        getContentPane().add(bConectar);

        scMsg.setBounds(20, 50, 300, 150);
        getContentPane().add(scMsg);

        scLis.setBounds(330, 50, 100, 150);
        getContentPane().add(scLis);

        mensajeTxt.setBounds(20, 210, 200, 30);
        getContentPane().add(mensajeTxt);

        btEnviar.setBounds(230, 210, 85, 30);
        btEnviar.addActionListener(e -> enviarMensaje());
        getContentPane().add(btEnviar);

        btAdjuntar.setBounds(325, 210, 115, 30);
        btAdjuntar.addActionListener(e -> adjuntarArchivo());
        getContentPane().add(btAdjuntar);

        modeloLista.addElement("Todos");
        setSize(470, 310);
        setLocationRelativeTo(null);
    }

    private void conectar() {
        nombreUsuario = JOptionPane.showInputDialog("Tu nombre:");
        try {
            socket = new Socket("localhost", PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(nombreUsuario);

            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String s;
                    while ((s = in.readLine()) != null) {
                        if (s.startsWith("LISTA:")) {
                            actualizarLista(s.substring(6));
                        } else if (s.startsWith("FILE:")) {
                            preguntarParaGuardar(s);
                        } else {
                            mensajesTxt.append(s + "\n");
                        }
                    }
                } catch (IOException e) { }
            }).start();
        } catch (Exception e) { e.printStackTrace(); }
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
        String dest = listaUsuariosUI.getSelectedValue();
        String m = mensajeTxt.getText();
        if (dest == null || dest.equals("Todos")) out.println(m);
        else {
            out.println("@" + dest + " " + m);
            mensajesTxt.append("[Para " + dest + "]: " + m + "\n");
        }
        mensajeTxt.setText("");
    }

    private void adjuntarArchivo() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f.length() > 1024) {
                JOptionPane.showMessageDialog(this, "El archivo debe ser menor a 1 KB");
                return;
            }
            try {
                byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
                String b64 = Base64.getEncoder().encodeToString(b);
                String dest = listaUsuariosUI.getSelectedValue();
                String cmd = "FILE:" + f.getName() + ":" + b64;

                if (dest == null || dest.equals("Todos")) out.println(cmd);
                else out.println("@" + dest + " " + cmd);

                mensajesTxt.append("Archivo enviado: " + f.getName() + "\n");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void preguntarParaGuardar(String raw) {
        String[] p = raw.split(":");
        String nombreOriginal = p[1];
        String base64Data = p[2];

        int op = JOptionPane.showConfirmDialog(this, "¿Deseas guardar el archivo: " + nombreOriginal + "?", "Archivo Recibido", JOptionPane.YES_NO_OPTION);

        if (op == JOptionPane.YES_OPTION) {
            JFileChooser saveFc = new JFileChooser();
            saveFc.setSelectedFile(new File(nombreOriginal));
            if (saveFc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileOutputStream fos = new FileOutputStream(saveFc.getSelectedFile())) {
                    byte[] data = Base64.getDecoder().decode(base64Data);
                    fos.write(data);
                    mensajesTxt.append(">> Archivo guardado: " + saveFc.getSelectedFile().getName() + "\n");
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalCli().setVisible(true));
    }
    private JButton bConectar, btEnviar, btAdjuntar;
    private JTextField mensajeTxt;
    private JTextArea mensajesTxt;
    private JList<String> listaUsuariosUI;
}