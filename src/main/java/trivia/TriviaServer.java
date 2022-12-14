package trivia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import trivia.model.Answer;
import trivia.model.Question;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TriviaServer {

    private static Set<TriviaUser> triviaUsers = new HashSet<>();
    private static Set<PrintWriter> clients = new HashSet<>();

    private static Set<Question> questions;

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


    public static class Handler  implements Runnable {

        private String userName;

        private Socket socket;
        private Scanner in;
        private PrintWriter out;

        private Timer timer;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    out.println("SEND.NAME");
                    userName = in.nextLine();
                    if (userName == null)
                        return;

                    synchronized (triviaUsers) {
                        if (!userName.isEmpty() && !triviaUsers.contains(new TriviaUser(userName))) {
                            triviaUsers.add(new TriviaUser(userName));
                            break;
                        }
                    }
                }

                out.println("NAME.ACCEPTED" + userName);

                for (PrintWriter klijent : clients) {
                    klijent.println("Poruka" + userName + " se pridruzio chatu");
                }

                clients.add(out);

                //if (triviaUsers.size() == 3) {
                    if (!triviaUsers.isEmpty()) {
                    out.println("GAME.START");
                }

                while (true) {

                    if (!questions.iterator().hasNext()) {
                        // find the winner
                        Set<TriviaUser> listResult = triviaUsers.stream().sorted(Comparator.comparing(TriviaUser::getResult).reversed()).collect(Collectors.toCollection(LinkedHashSet::new));
                        out.println("THE.END");
                        out.println(listResult);
                    }

                    Question currentQuestion = questions.iterator().next();
//                    if (triviaUsers.size() != 3) {
//                        out.println("THE.END");
//                        return;
//                    }

                    // postavljamo pitanja - send to clients
                    for (PrintWriter klijent : clients) {
                        klijent.println("Q.START");
                        klijent.println(currentQuestion);
                    }

                    boolean moveToNextQuestion = false;
                    int counter = 1;
                    while (!moveToNextQuestion) {

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

                       if(taskCompleted.get()){
                           break;
                       }

                        String ulaz = in.nextLine();
                        if (ulaz.toLowerCase().startsWith("/kraj")) {
                            return;
                        } else if (ulaz.startsWith("ANSWER")) {


                            // procitamo odgovor
                            try {
                                // accepted answers: A, B, C
                                String answer = ulaz.substring(6);
                                System.out.println("Answer " + answer);
                                if (answer.equals("0")) {
                                    out.println("Game over");
                                    return;
                                } else if (!answer.equals("A") && !answer.equals("B") && !answer.equals("C")) {
                                    System.out.println("Wrong choice");
                                    out.println("ERROR.CHOICE");
                                }

                                String clientName = in.nextLine().substring(4);

                                // find the answer
                                Optional<Answer> correctAnswer = currentQuestion.getAnswers()
                                        .stream()
                                        .filter(a -> a.getChoice().equals(answer)).findFirst();
                                if (correctAnswer.isPresent()) {
                                    if (correctAnswer.get().isCorrect()) {
                                        // find client
                                        TriviaUser client = triviaUsers.stream().filter(c -> c.getName().equals(clientName)).findFirst().get();
                                        client.increaseResult();
                                    }
                                }

                            } catch (Exception e) {
                                System.out.println("Wrong choice");
                                out.println("ERROR.CHOICE");
                            }
                        }

                        counter++; // count number of clients that answered
                        //if (counter == 3) {
                        if (counter == 2) {
                            moveToNextQuestion = true;
                            counter = 1;
                        }
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(TriviaServer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (out != null)
                    clients.remove(out);

                if (userName != null) {
                    triviaUsers.remove(userName);
                    for (PrintWriter klijent : clients) {
                        klijent.println("Poruka:" + userName + " je napustio/la chat");
                    }
                }

                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
