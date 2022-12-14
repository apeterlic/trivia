package trivia;

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
        txtArea.setEnabled(false);
        frame.getContentPane().add(txtChat, BorderLayout.SOUTH);
        frame.getContentPane().add(txtArea, BorderLayout.CENTER);
        frame.pack();

        txtChat.addActionListener(e -> {
            out.println("ANSWER" + txtChat.getText()); // send the answer to the server
            txtArea.append(txtChat.getText());
            out.println("NAME" + triviaUser.getName()); // send the name to the server
            txtChat.setText("");
        });
    }


    private String getIme() {
        return JOptionPane.showInputDialog(frame, "Odaberite ime", "Odabir imena", JOptionPane.PLAIN_MESSAGE);
    }

    private void run() throws IOException {
        try {
            Socket socket = new Socket(serverAddress, 59001);
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.startsWith("SEND.NAME")) {
                    String ime = getIme(); // enter username
                    triviaUser = new TriviaUser(ime); // create TriviaUser
                    out.println(ime); // send username to the server
                } else if (line.startsWith("NAME.ACCEPTED")) {
                    this.frame.setTitle("Trivia - " + line.substring(14));
                } else if (line.startsWith("Poruka")) {
                    txtArea.append(line.substring(6) + "\n");
                } else if (line.startsWith("Q.START")) {
                    txtArea.append(in.nextLine() + "\n"); // pitanje
                    txtArea.append(in.nextLine() + "\n"); // odg1
                    txtArea.append(in.nextLine() + "\n"); // odg2
                    txtArea.append(in.nextLine() + "\n"); // odg3
                } else if (line.startsWith("GAME.START")) {
                    txtArea.setEnabled(true);
                    txtArea.append("START" + "\n" + "\n");
                } else if (line.startsWith("ERROR.CHOICE")) {
                    txtArea.append("Molim unestie ispravnu vrijednost (ili 0 za kraj)");
                } else if (line.startsWith("THE.END")) {
                    txtArea.append("Game ended." + "\n");
                    while (in.hasNextLine()) {
                        txtArea.append(in.nextLine() + "\n");
                    }
                }
            }
        } finally {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    public static void main(String[] args) throws IOException {
        TriviaClient triviaClient = new TriviaClient("127.0.0.1");
        triviaClient.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        triviaClient.frame.setVisible(true);
        triviaClient.run();
    }
}
