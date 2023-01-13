package trivia;

import trivia.model.TriviaUser;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TriviaClient {

    String serverAddress;
    Scanner in;
    PrintWriter out;
    JFrame frame = new JFrame("Trivia");
    JTextField txtChat = new JTextField(50);
    JTextArea txtArea = new JTextArea(16, 50);

    TriviaUser triviaUser;

    public TriviaClient(final String serverAddress) {
        this.serverAddress = serverAddress;
        txtChat.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(txtArea);   // JTextArea is placed in a JScrollPane.

        frame.getContentPane().add(txtChat, BorderLayout.SOUTH);
        //frame.getContentPane().add(txtArea, BorderLayout.CENTER);
        frame.getContentPane().add(scrollPane);
        frame.pack();

        txtChat.addActionListener(e -> {
            out.println("ANSWER" + txtChat.getText() + ";NAME" + triviaUser.getName()); // send the answer to the server
            txtArea.append(txtChat.getText() + "\n");
            //out.println("NAME" + triviaUser.getName()); // send the name to the server
            txtChat.setText("");
        });
    }

    public static void main(String[] args) throws IOException {
        TriviaClient triviaClient = new TriviaClient("127.0.0.1");
        triviaClient.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        triviaClient.frame.setVisible(true);
        triviaClient.run();
    }

    private String getIme() {
        return JOptionPane.showInputDialog(frame, "Your Name", "Enter Name", JOptionPane.PLAIN_MESSAGE);
    }

    private void run() throws IOException {
        try {
            Socket socket = new Socket(serverAddress, 59001);
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.startsWith("SEND.NAME")) {
                    String name = getIme(); // enter username
                    triviaUser = TriviaUser.newInstance(name); // create TriviaUser
                    out.println(name); // send username to the server
                } else if (line.startsWith("NAME.ACCEPTED")) {
                    this.frame.setTitle("Trivia - " + line.substring(13));
                } else if (line.startsWith("INFO")) {
                    txtArea.append(line.substring(4) + "\n");
                } else if (line.startsWith("Q.START")) {
                    txtArea.append(in.nextLine() + "\n"); // pitanje
                    txtArea.append(in.nextLine() + "\n"); // odg1
                    txtArea.append(in.nextLine() + "\n"); // odg2
                    txtArea.append(in.nextLine() + "\n"); // odg3
                } else if (line.startsWith("GAME.START")) {
                    txtChat.setEnabled(true);
                    txtArea.append("GAME STARTED!" + "\n");
                } else if (line.startsWith("ERROR.CHOICE")) {
                    txtArea.append("Please select correct answer (or 0 to end the game)" + "\n");
                } else if (line.startsWith("GAME.WAIT")) {
                    txtArea.append("Waiting for others to join. " + "\n");
                } else if (line.startsWith("THE.END")) {
                    txtArea.append("Game ended." + "\n");
                } else if (line.startsWith("GAME.FINISHED")) {
                    txtArea.append("Game result." + "\n");
                    txtArea.append(line.substring(13) + "\n");
                }
            }
        } finally {
            frame.setVisible(false);
            frame.dispose();
        }
    }
}
