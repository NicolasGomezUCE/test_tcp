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

    public PrincipalCli() {
        setTitle("Cliente Resiliente");
        btnCon.addActionListener(e -> conectar(1));
        campo.addActionListener(e -> enviar());
        setLayout(new BorderLayout());
        add(btnCon, BorderLayout.NORTH);
        add(new JScrollPane(listaUI), BorderLayout.WEST);
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(campo, BorderLayout.SOUTH);
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void conectar(int intento) {
        if (nombre == null) nombre = JOptionPane.showInputDialog("Nombre:");
        if (nombre == null || intento > 3) {
            btnCon.setEnabled(true);
            return;
        }

        try {
            btnCon.setEnabled(false);
            if (socket != null) socket.close();

            socket = new Socket("localhost", 12345);
            // IMPORTANTE: Definir timeout o autoFlush
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(nombre);

            new Thread(this::escuchar).start();
            area.append("[SISTEMA] Conectado.\n");
        } catch (Exception e) {
            area.append("[!] Reintento " + intento + "/3...\n");
            new Thread(() -> { try { Thread.sleep(2000); conectar(intento + 1); } catch (Exception ex) {} }).start();
        }
    }

    private void escuchar() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String s;
            while ((s = in.readLine()) != null) {
                if (s.startsWith("LISTA:")) actualizarLista(s.substring(6));
                else area.append(s + "\n");
            }
        } catch (Exception e) {
            area.append("[!] Conexión perdida. Saltando de nodo...\n");
        } finally {
            conectar(1); // Al salir del while por error, intenta reconectar al LB
        }
    }

    private void enviar() {
        String m = campo.getText();
        if (m.isEmpty() || out == null) return;

        try {
            String dest = listaUI.getSelectedValue();
            if (dest != null) {
                out.println("@" + dest + " " + m);
                area.append("[Privado para " + dest + "]: " + m + "\n");
            } else {
                out.println(m);
            }
            // Si el socket estuviera muerto, checkError() puede ayudar a detectarlo
            if (out.checkError()) throw new IOException("Error de escritura");
            campo.setText("");
        } catch (Exception e) {
            area.append("[SISTEMA] Error al enviar. Reconectando...\n");
            conectar(1);
        }
    }

    private void actualizarLista(String d) {
        SwingUtilities.invokeLater(() -> {
            modelo.clear();
            for (String u : d.split(",")) if (!u.equals(nombre)) modelo.addElement(u);
        });
    }

    public static void main(String[] args) { new PrincipalCli().setVisible(true); }
}