package lab.pkgfinal;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Server {
    private static final int MESSAGE_PORT = 12345;
    private static final int FILE_PORT = 12346;
    private static final int MAX_CLIENTS = 5;
    private final Map<String, ClientHandler> clients = Collections.synchronizedMap(new HashMap<>());
    private boolean isRunning = false;

    private JTextArea chatArea, logArea;

    public Server(JTextArea chatArea, JTextArea logArea) {
        this.chatArea = chatArea;
        this.logArea = logArea;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        new Thread(this::startMessageServer).start();
        new Thread(this::startFileServer).start();
        log("Server started.");
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        log("Server stopped.");
    }

    private void startMessageServer() {
        try (ServerSocket serverSocket = new ServerSocket(MESSAGE_PORT)) {
            log("Message server listening on port " + MESSAGE_PORT);
            while (isRunning) {
                if (clients.size() >= MAX_CLIENTS) continue;

                Socket clientSocket = serverSocket.accept();
                log("New client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            log("Error in message server: " + e.getMessage());
        }
    }

    private void startFileServer() {
        try (ServerSocket serverSocket = new ServerSocket(FILE_PORT)) {
            log("File server listening on port " + FILE_PORT);
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                log("File server: New client connected: " + clientSocket.getInetAddress());

                new Thread(() -> handleFileTransfer(clientSocket)).start();
            }
        } catch (IOException e) {
            log("Error in file server: " + e.getMessage());
        }
    }

    private void handleFileTransfer(Socket clientSocket) {
        try (InputStream is = clientSocket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String command = reader.readLine();
            if (command.startsWith("/file")) {
                String fileName = command.split(" ")[1];
                File file = new File("server_files/" + fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                log("File received: " + fileName);
            } else if (command.startsWith("/download")) {
                String fileName = command.split(" ")[1];
                File file = new File("server_files/" + fileName);
                if (file.exists()) {
                    try (OutputStream os = clientSocket.getOutputStream();
                         FileInputStream fis = new FileInputStream(file)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                    log("File sent: " + fileName);
                } else {
                    log("File not found: " + fileName);
                }
            }
        } catch (IOException e) {
            log("Error handling file transfer: " + e.getMessage());
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    private void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients.values()) {
                client.sendMessage(message);
            }
        }
        SwingUtilities.invokeLater(() -> chatArea.append("Group: " + message + "\n"));
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter writer;
        private String username;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                writer = new PrintWriter(clientSocket.getOutputStream(), true);

                writer.println("Enter your username:");
                username = reader.readLine();
                if (username == null || username.trim().isEmpty()) {
                    log("Client connection aborted due to missing username.");
                    return;
                }

                clients.put(username, this);
                broadcast(username + " joined the chat.");

                String message;
                while ((message = reader.readLine()) != null) {
                    handleClientMessage(message);
                }
            } catch (IOException e) {
                log("Client disconnected: " + username);
            } finally {
                clients.remove(username);
                broadcast(username + " left the chat.");
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleClientMessage(String message) {
            log("Received from " + username + ": " + message);

            if (message.startsWith("/private")) {
                String[] parts = message.split(" ", 3);
                if (parts.length == 3) {
                    String targetUsername = parts[1];
                    String privateMessage = parts[2];
                    ClientHandler targetClient = clients.get(targetUsername);
                    if (targetClient != null) {
                        targetClient.sendMessage("[Private from " + username + "] " + privateMessage);
                    } else {
                        sendMessage("User " + targetUsername + " not found.");
                    }
                }
            } else if (message.startsWith("/poll")) {
                String pollQuestion = message.substring(6);
                broadcast("Poll created: " + pollQuestion + " (Vote with /vote <option>)");
            } else if (message.startsWith("/vote")) {
                String voteOption = message.substring(6);
                broadcast(username + " voted: " + voteOption);
            } else {
                broadcast(username + ": " + message);
            }
        }

        public void sendMessage(String message) {
            writer.println(message);
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Server");
        JTextArea chatArea = new JTextArea(15, 40);
        JTextArea logArea = new JTextArea(10, 40);
        JButton startButton = new JButton("Start Server");
        JButton stopButton = new JButton("Stop Server");

        Server server = new Server(chatArea, logArea);

        startButton.addActionListener(e -> server.start());
        stopButton.addActionListener(e -> server.stop());

        JPanel controlPanel = new JPanel();
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        frame.add(new JScrollPane(chatArea), "Center");
        frame.add(new JScrollPane(logArea), "South");
        frame.add(controlPanel, "North");

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
