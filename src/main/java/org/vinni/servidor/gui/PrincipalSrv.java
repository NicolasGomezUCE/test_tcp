package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Vinni - Modificado para múltiples clientes
 */
public class PrincipalSrv extends javax.swing.JFrame {
    private final int PORT = 12345;
    private ServerSocket serverSocket;
    // Lista para almacenar los flujos de salida de todos los clientes conectados
    private final List<PrintWriter> clientesConectados = new ArrayList<>();

    public PrincipalSrv() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        this.setTitle("Servidor Multicliente");

        bIniciar = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        mensajesTxt = new JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bIniciar.setFont(new java.awt.Font("Segoe UI", 0, 18));
        bIniciar.setText("INICIAR SERVIDOR");
        bIniciar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bIniciarActionPerformed(evt);
            }
        });
        getContentPane().add(bIniciar);
        bIniciar.setBounds(100, 90, 250, 40);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setForeground(new java.awt.Color(204, 0, 0));
        jLabel1.setText("SERVIDOR TCP : MULTI-USUARIO");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(150, 10, 260, 17);

        mensajesTxt.setColumns(25);
        mensajesTxt.setRows(5);
        jScrollPane1.setViewportView(mensajesTxt);

        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 160, 410, 70);

        setSize(new java.awt.Dimension(491, 290));
        setLocationRelativeTo(null);
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }

    private void bIniciarActionPerformed(java.awt.event.ActionEvent evt) {
        iniciarServidor();
    }

    private void iniciarServidor() {
        JOptionPane.showMessageDialog(this, "Iniciando servidor multicliente");
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getLocalHost();
                serverSocket = new ServerSocket(PORT);
                mensajesTxt.append("Servidor en: " + addr + " puerto " + PORT + "\n");

                while (true) {
                    // Acepta un nuevo cliente y lanza un hilo para manejarlo
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ManejadorCliente(clientSocket)).start();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    /**
     * Clase interna para manejar la comunicación individual de cada cliente
     */
    private class ManejadorCliente implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private String nombreCliente;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);

                // El primer mensaje enviado por el cliente será su nombre
                this.nombreCliente = in.readLine();

                synchronized (clientesConectados) {
                    clientesConectados.add(out);
                }

                difundirMensaje("SISTEMA", nombreCliente + " se ha conectado.");

                String linea;
                while ((linea = in.readLine()) != null) {
                    difundirMensaje(nombreCliente, linea);
                }
            } catch (IOException ex) {
                System.out.println("Cliente desconectado: " + nombreCliente);
            } finally {
                synchronized (clientesConectados) {
                    clientesConectados.remove(out);
                }
                difundirMensaje("SISTEMA", nombreCliente + " ha salido.");
                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    /**
     * Envía el mensaje a todos los clientes en la lista
     */
    private void difundirMensaje(String emisor, String mensaje) {
        String mensajeFormateado = emisor + ": " + mensaje;
        mensajesTxt.append(mensajeFormateado + "\n");

        synchronized (clientesConectados) {
            for (PrintWriter cliente : clientesConectados) {
                cliente.println(mensajeFormateado);
            }
        }
    }

    private javax.swing.JButton bIniciar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
}