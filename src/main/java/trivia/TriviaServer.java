package trivia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import trivia.model.Answer;
import trivia.model.Question;
import trivia.model.TriviaUser;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TriviaServer {


    private static final int USER_NUMBER = 3;

    private static final Set<TriviaUser> triviaUsers = new HashSet<>();
    private static final List<PrintWriter> clients = new ArrayList<>();
    private static final List<Scanner> clientsIn = new ArrayList<>();

    private static final List<Question> questions;

    static {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // read questions from the file
            questions = objectMapper.readValue(new File("dat/pitanja.json"), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        System.out.println("Trivia server started");
        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(500);

        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static class Handler implements Runnable {

        private String userName;

        private final Socket clientSocket;
        private Scanner in;
        private PrintWriter out;

        private Timer timer;

        public Handler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {
                System.out.println("New thread for new user!");
                in = new Scanner(clientSocket.getInputStream());
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                while (true) {
                    out.println("SEND.NAME");
                    userName = in.nextLine();
                    if (userName == null)
                        return;

                    synchronized (triviaUsers) {
                        if (!userName.isEmpty() && !triviaUsers.stream().anyMatch(u -> u.getName().equals(userName))) {
                            triviaUsers.add(TriviaUser.newInstance(userName));
                            break;
                        }
                    }
                }

                out.println("NAME.ACCEPTED" + userName);

                sendJoinedInfo();

                clients.add(out);
                clientsIn.add(in);

                if (triviaUsers.size() != USER_NUMBER) {
                    out.println("GAME.WAIT");
                }

                while (true) {
                    if (triviaUsers.size() == USER_NUMBER) {
                        System.out.println("Everyone joined");
                        break;
                    }
                }

                System.out.println("Sending info to clients");
                for (PrintWriter client : clients) {
                    client.println("GAME.START");
                }

                int questionCounter = 0;
                while (true) {

                    if (questionCounter == questions.size()) {
                        // find the winner
                        Set<TriviaUser> listResult = triviaUsers
                                .stream()
                                .sorted(Comparator.comparing(TriviaUser::getResult).reversed())
                                .collect(Collectors.toCollection(LinkedHashSet::new));

                        for (PrintWriter client : clients) {
                            client.println("GAME.FINISHED" + listResult);
                        }
                        questionCounter++;
                    } else if (questionCounter < questions.size()) {

                        // get question by index
                        Question currentQuestion = questions.get(questionCounter);
                        questionCounter++;

                        // send question to client
                        for (PrintWriter client : clients) {
                            client.println("Q.START");
                            client.println(currentQuestion);
                        }

                        boolean moveToNextQuestion = false;
                        int counter = 0;
                        while (!moveToNextQuestion) {

                            AtomicBoolean taskCompleted = createTimer();

                            if (taskCompleted.get()) {
                                break;
                            }

                            for (Scanner client : clientsIn) {

                                boolean inputValid = false;
                                String answer = null;
                                String[] inputs = null;

                                while (!inputValid) {

                                    // read the answer from a client
                                    String userInput = client.nextLine();
                                    System.out.println("User input " + userInput);
                                    if (userInput.toLowerCase().startsWith("/kraj")) {
                                        return;
                                    } else if (userInput.startsWith("ANSWER")) {

                                        try {
                                            // accepted answers: A, B, C
                                            inputs = userInput.split(";");

                                            answer = inputs[0].substring(6);
                                            System.out.println("Answer " + answer);

                                            if (answer.equals("0")) {
                                                for (PrintWriter c : clients) {
                                                    c.println("GAME.FINISHED" + c);
                                                }
                                                return;
                                            } else if (!answer.equalsIgnoreCase("A") && !answer.equalsIgnoreCase("B") && !answer.equalsIgnoreCase("C")) {
                                                System.out.println("Wrong choice");
                                                int clientIndex = clientsIn.indexOf(client);
                                                clients.get(clientIndex).println("ERROR.CHOICE");
                                            } else {
                                                inputValid = true;
                                                break;
                                            }
                                        } catch (Exception e) {
                                            System.out.println("Wrong choice");
                                            int clientIndex = clientsIn.indexOf(client);
                                            clients.get(clientIndex).println("ERROR.CHOICE");
                                        }
                                    }
                                }

                                String clientName = inputs[1].substring(4);

                                // find the answer
                                String finalAnswer = answer;
                                Optional<Answer> correctAnswer = currentQuestion.getAnswers()
                                        .stream()
                                        .filter(a -> a.getChoice().equalsIgnoreCase(finalAnswer)).findFirst();
                                if (correctAnswer.isPresent()) {
                                    if (correctAnswer.get().isCorrect()) {
                                        // find client
                                        TriviaUser user = triviaUsers.stream().filter(c -> c.getName().equals(clientName)).findFirst().get();
                                        user.increaseResult();
                                    }
                                }
                                counter++;
                            }

                            if (counter == USER_NUMBER) {
                                moveToNextQuestion = true;
                                counter = 0;
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                System.out.println("ERROR " + ex);
                Logger.getLogger(TriviaServer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (out != null)
                    clients.remove(out);

                if (userName != null) {
                    triviaUsers.remove(userName);
                    for (PrintWriter client : clients) {
                        client.println("INFO" + userName + " has left the game");
                    }
                }

                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private AtomicBoolean createTimer() {
            AtomicBoolean taskCompleted = new AtomicBoolean(false);
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Time is ower");
                    timer.cancel(); //stop the thread of timer
                    taskCompleted.set(true);
                }
            }, 10 * 1000);
            return taskCompleted;
        }

        private void sendJoinedInfo() {
            for (PrintWriter client : clients) {
                client.println("INFO" + userName + " joined the game");
            }
        }

    }

}
