package org.vinni.servidor.gui;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PrincipalSrv extends javax.swing.JFrame {
    private final int PORT = 12345;
    private ServerSocket serverSocket;
    // Uso de ConcurrentHashMap para evitar errores de concurrencia en ráfagas de mensajes
    private final Map<String, PrintWriter> mapaClientes = new ConcurrentHashMap<>();
    private boolean autoReinicio = true;

    public PrincipalSrv() {
        initComponents();
        configurarEstilo();
    }

    private void configurarEstilo() {
        getContentPane().setBackground(new Color(33, 37, 41));
        mensajesTxt.setBackground(new Color(15, 15, 15));
        mensajesTxt.setForeground(new Color(50, 255, 50));
        mensajesTxt.setFont(new Font("Monospaced", Font.PLAIN, 12));
    }

    private void initComponents() {
        this.setTitle("Panel de Control - Servidor");
        bIniciar = new JButton("Iniciar");
        bFallo = new JButton("Fallo Crítico");
        bLimpiar = new JButton("Limpiar Log");
        mensajesTxt = new JTextArea();
        JScrollPane scroll = new JScrollPane(mensajesTxt);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bIniciar.setBounds(20, 20, 130, 35);
        bIniciar.addActionListener(e -> iniciarServidor());
        getContentPane().add(bIniciar);

        bFallo.setBounds(160, 20, 130, 35);
        bFallo.setBackground(new Color(220, 53, 69));
        bFallo.setForeground(Color.WHITE);
        bFallo.addActionListener(e -> simularFallo());
        getContentPane().add(bFallo);

        bLimpiar.setBounds(300, 20, 130, 35);
        bLimpiar.addActionListener(e -> mensajesTxt.setText(""));
        getContentPane().add(bLimpiar);

        scroll.setBounds(20, 70, 410, 180);
        getContentPane().add(scroll);

        setSize(470, 310);
        setLocationRelativeTo(null);
    }

    private void iniciarServidor() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                log("[SISTEMA] Escuchando en puerto " + PORT);
                bIniciar.setEnabled(false);
                while (!serverSocket.isClosed()) {
                    Socket s = serverSocket.accept();
                    new Thread(new ManejadorCliente(s)).start();
                }
            } catch (IOException e) {
                if (autoReinicio) {
                    log("[POLÍTICA] Servidor caído. Reiniciando automáticamente...");
                    intentarReinicio();
                }
            }
        }).start();
    }

    private void simularFallo() {
        try {
            log("[ALERTA] Forzando desconexión de todos los clientes...");
            // Cierre total de flujos activos
            mapaClientes.forEach((nombre, pw) -> {
                pw.close();
            });
            mapaClientes.clear();

            if (serverSocket != null) serverSocket.close();
            bIniciar.setEnabled(true);
        } catch (IOException e) {
            log("[ERROR] Al cerrar: " + e.getMessage());
        }
    }

    private void intentarReinicio() {
        try { Thread.sleep(3000); iniciarServidor(); } catch (Exception e) {}
    }

    private void log(String m) {
        SwingUtilities.invokeLater(() -> mensajesTxt.append(m + "\n"));
    }

    private class ManejadorCliente implements Runnable {
        private Socket socket;
        private String nombre;

        public ManejadorCliente(Socket s) { this.socket = s; }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                this.nombre = in.readLine();
                if (nombre == null) return;

                mapaClientes.put(nombre, out);
                difundirLista();
                log("[USER] " + nombre + " conectado.");

                String linea;
                while ((linea = in.readLine()) != null) {
                    if (linea.startsWith("@")) procesarPrivado(linea);
                    else difundirMensaje(nombre, linea);
                }
            } catch (IOException e) {
                log("[USER] " + nombre + " desconectado.");
            } finally {
                mapaClientes.remove(nombre);
                difundirLista();
            }
        }

        private void procesarPrivado(String msg) {
            int espacio = msg.indexOf(" ");
            if (espacio != -1) {
                String dest = msg.substring(1, espacio);
                String contenido = msg.substring(espacio + 1);
                PrintWriter pw = mapaClientes.get(dest);

                if (pw != null) {
                    // LOG REQUERIDO: Registro de quién a quién
                    log("[PRIVADO] De: " + nombre + " -> Para: " + dest);
                    pw.println(contenido.startsWith("FILE:") ? contenido : "[Privado de " + nombre + "]: " + contenido);
                }
            }
        }

        private void difundirMensaje(String emisor, String msg) {
            if (!msg.startsWith("FILE:")) log("[CHAT] " + emisor + ": " + msg);
            String trama = msg.startsWith("FILE:") ? msg : emisor + ": " + msg;
            mapaClientes.values().forEach(p -> p.println(trama));
        }

        private void difundirLista() {
            String lista = "LISTA:" + String.join(",", mapaClientes.keySet());
            mapaClientes.values().forEach(p -> p.println(lista));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
    private JButton bIniciar, bFallo, bLimpiar;
    private JTextArea mensajesTxt;
}