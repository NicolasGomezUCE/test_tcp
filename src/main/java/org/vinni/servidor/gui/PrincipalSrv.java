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
        this.setTitle("Servidor Chat & Archivos");
        bIniciar = new JButton("INICIAR SERVIDOR");
        mensajesTxt = new JTextArea();
        JScrollPane scroll = new JScrollPane(mensajesTxt);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bIniciar.setBounds(100, 30, 250, 40);
        bIniciar.addActionListener(e -> iniciarServidor());
        getContentPane().add(bIniciar);

        scroll.setBounds(20, 90, 410, 150);
        getContentPane().add(scroll);

        setSize(470, 300);
        setLocationRelativeTo(null);
    }

    private void iniciarServidor() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                mensajesTxt.append("Servidor iniciado en puerto " + PORT + "\n");
                while (true) {
                    Socket s = serverSocket.accept();
                    new Thread(new ManejadorCliente(s)).start();
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void difundirLista() {
        StringBuilder sb = new StringBuilder("LISTA:");
        synchronized (mapaClientes) {
            mapaClientes.keySet().forEach(n -> sb.append(n).append(","));
        }
        String lista = sb.toString();
        synchronized (mapaClientes) {
            mapaClientes.values().forEach(p -> p.println(lista));
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
                difundirLista();

                String linea;
                while ((linea = in.readLine()) != null) {
                    if (linea.startsWith("@")) {
                        enviarPrivado(linea);
                    } else {
                        difundirMensaje(nombre, linea);
                    }
                }
            } catch (IOException e) {
            } finally {
                synchronized (mapaClientes) { mapaClientes.remove(nombre); }
                difundirLista();
            }
        }

        private void enviarPrivado(String msg) {
            int espacio = msg.indexOf(" ");
            if (espacio != -1) {
                String dest = msg.substring(1, espacio);
                String contenido = msg.substring(espacio + 1);
                PrintWriter pw = mapaClientes.get(dest);
                if (pw != null) pw.println(contenido.startsWith("FILE:") ? contenido : "[Privado de " + nombre + "]: " + contenido);
            }
        }

        private void difundirMensaje(String emisor, String msg) {
            String f = msg.startsWith("FILE:") ? msg : emisor + ": " + msg;
            if (!msg.startsWith("FILE:")) mensajesTxt.append(emisor + ": " + msg + "\n");
            synchronized (mapaClientes) {
                mapaClientes.values().forEach(p -> p.println(f));
            }
        }
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
    private JButton bIniciar;
    private JTextArea mensajesTxt;
}