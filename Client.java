package lab.pkgfinal;

import java.io.*;
import java.net.*;
import javax.swing.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int MESSAGE_PORT = 12345;
    private static final int FILE_PORT = 12346;

    private JTextArea chatArea, logArea;
    private JTextField inputField;
    private String username;
    private Socket messageSocket, fileSocket;
    private BufferedReader reader;
    private PrintWriter writer;

    public Client(JTextArea chatArea, JTextArea logArea, JTextField inputField) {
        this.chatArea = chatArea;
        this.logArea = logArea;
        this.inputField = inputField;
    }

    public void connectToServer() {
        try {
            log("Attempting to connect to server...");

            messageSocket = new Socket(SERVER_ADDRESS, MESSAGE_PORT);
            reader = new BufferedReader(new InputStreamReader(messageSocket.getInputStream()));
            writer = new PrintWriter(messageSocket.getOutputStream(), true);

            //username add
            username = JOptionPane.showInputDialog("Enter your username:");
            if (username == null || username.trim().isEmpty()) {
                log("Username cannot be empty. Connection aborted.");
                return;
            }
            writer.println(username);

            new Thread(this::listenForMessages).start();
            log("Connected to server as: " + username);
        } catch (IOException e) {
            log("Error connecting to server: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            String receivedMessage;
            while ((receivedMessage = reader.readLine())!= null) {
                final String message = receivedMessage;
                SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
            }
        } catch (IOException e) {
            log("Error reading messages: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    public void sendMessage(String message) {
        if (messageSocket == null || writer == null) {
            log("Not connected to the server.");
            return;
        }
        writer.println(message);
        log("Sent: " + message);
    }

    public void sendFile(File file) {
        try {
            log("Attempting to send file: " + file.getName());

            fileSocket = new Socket(SERVER_ADDRESS, FILE_PORT);

            try (OutputStream os = fileSocket.getOutputStream();
                 PrintWriter pw = new PrintWriter(os, true);
                 FileInputStream fis = new FileInputStream(file)) {

                pw.println("/file " + file.getName());

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
                log("File sent: " + file.getName());
            }
            fileSocket.close();
        } catch (IOException e) {
            log("Error sending file: " + e.getMessage());
        }
    }

public void downloadFile(String fileName) {
    try {
        log("Attempting to download file: " + fileName);

        // Ensure the client_files directory exists
        File downloadDir = new File("client_files");
        
        if (!downloadDir.exists()) {
            if (downloadDir.mkdir()) {
                log("Directory 'client_files' created.");
            } else {
                log("Failed to create 'client_files' directory.");
                return;
            }
        }

        fileSocket = new Socket(SERVER_ADDRESS, FILE_PORT);

        try (OutputStream os = new FileOutputStream(new File(downloadDir, fileName));
             InputStream is = fileSocket.getInputStream();
             PrintWriter pw = new PrintWriter(fileSocket.getOutputStream(), true)) {

            pw.println("/download " + fileName);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            log("File downloaded: " + fileName);
        }
        fileSocket.close();
    } catch (IOException e) {
        log("Error downloading file: " + e.getMessage());
    }
}


    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    private void disconnect() {
        try {
            if (messageSocket != null) {
                messageSocket.close();
            }
            log("Disconnected from server.");
        } catch (IOException e) {
            log("Error disconnecting: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Client");
        JTextArea chatArea = new JTextArea(15, 40);
        chatArea.setEditable(false);
        JTextArea logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        JTextField inputField = new JTextField(30);
        JButton sendButton = new JButton("Send");
        JButton sendFileButton = new JButton("Send File");
        JButton downloadButton = new JButton("Download File");

        Client client = new Client(chatArea, logArea, inputField);

        // Connect to server
        SwingUtilities.invokeLater(client::connectToServer);

        // Send Message
        sendButton.addActionListener(e -> {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                client.sendMessage(message);
                inputField.setText("");
            }
        });

        // Send File
        sendFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                client.sendFile(selectedFile);
            }
        });

        // Download File
        downloadButton.addActionListener(e -> {
            String fileName = JOptionPane.showInputDialog("Enter the file name to download:");
            if (fileName != null && !fileName.trim().isEmpty()) {
                client.downloadFile(fileName);
            }
        });

        JPanel inputPanel = new JPanel();
        inputPanel.add(inputField);
        inputPanel.add(sendButton);
        inputPanel.add(sendFileButton);
        inputPanel.add(downloadButton);

        frame.add(new JScrollPane(chatArea), "Center");
        frame.add(new JScrollPane(logArea), "South");
        frame.add(inputPanel, "North");

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
