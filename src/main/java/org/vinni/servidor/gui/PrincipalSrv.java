package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class PrincipalSrv extends javax.swing.JFrame {
    private final int PORT = 12345;
    private ServerSocket serverSocket;
    private final Map<String, PrintWriter> mapaClientes = new HashMap<>();

    public PrincipalSrv() {
        initComponents();
    }

    private void initComponents() {
        this.setTitle("Servidor con Selección de Destinatario");
        bIniciar = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        mensajesTxt = new JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bIniciar.setText("INICIAR SERVIDOR");
        bIniciar.addActionListener(evt -> iniciarServidor());
        getContentPane().add(bIniciar);
        bIniciar.setBounds(100, 50, 250, 40);

        mensajesTxt.setColumns(25);
        mensajesTxt.setRows(5);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 110, 410, 120);

        setSize(480, 300);
        setLocationRelativeTo(null);
    }

    private void iniciarServidor() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                mensajesTxt.append("Servidor iniciado en puerto " + PORT + "\n");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ManejadorCliente(clientSocket)).start();
                }
            } catch (IOException ex) { ex.printStackTrace(); }
        }).start();
    }

    private void difundirListaUsuarios() {
        StringBuilder sb = new StringBuilder("LISTA:");
        synchronized (mapaClientes) {
            for (String nombre : mapaClientes.keySet()) sb.append(nombre).append(",");
        }
        String lista = sb.toString();
        synchronized (mapaClientes) {
            for (PrintWriter p : mapaClientes.values()) p.println(lista);
        }
    }

    private class ManejadorCliente implements Runnable {
        private Socket socket;
        private String nombre;

        public ManejadorCliente(Socket s) { this.socket = s; }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                this.nombre = in.readLine();

                synchronized (mapaClientes) { mapaClientes.put(nombre, out); }
                difundirListaUsuarios();

                String linea;
                while ((linea = in.readLine()) != null) {
                    if (linea.startsWith("@")) {
                        enviarPrivado(linea);
                    } else {
                        difundirMensaje(nombre, linea);
                    }
                }
            } catch (IOException e) { } finally {
                synchronized (mapaClientes) { mapaClientes.remove(nombre); }
                difundirListaUsuarios();
            }
        }

        private void enviarPrivado(String msg) {
            int espacio = msg.indexOf(" ");
            if (espacio != -1) {
                String destino = msg.substring(1, espacio);
                String contenido = msg.substring(espacio + 1);
                PrintWriter pw = mapaClientes.get(destino);
                if (pw != null) pw.println("[Privado de " + nombre + "]: " + contenido);
            }
        }
    }

    private void difundirMensaje(String emisor, String m) {
        String f = emisor + ": " + m;
        mensajesTxt.append(f + "\n");
        synchronized (mapaClientes) {
            for (PrintWriter p : mapaClientes.values()) p.println(f);
        }
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }

    private javax.swing.JButton bIniciar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}