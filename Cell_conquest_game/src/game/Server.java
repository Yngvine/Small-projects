/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package game;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server extends Thread {

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

    final int port = 12000;

    final List<ClientThread> clients = new LinkedList<>();
    public Map<String, Country> countriesByColor = new HashMap<>();
    public final List<Country> countries = Country.readCountries("countriesWithColAndCentroids.csv");
    public List<Player> players = new ArrayList<>();

    public Server() {

        for (Country c : countries) {
            countriesByColor.put(c.getColor(), c);
        }

        (new Thread() {
            public void run() {
                while (true) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {

                        gameTick();                                         //Updates game state

                    } catch (IOException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }).start();
    }

    @Override
    public void run() {
        try ( ServerSocket serverSocket = new ServerSocket(port);) {
            System.out.println("Started Chat server on port " + port);
            // repeatedly wait for connections
            while (!interrupted()) {
                Socket clientSocket = serverSocket.accept();
                ClientThread clientThread = new ClientThread(clients, clientSocket, randomUserID());
                clientThread.start();
            }
        } catch (IOException ex) {
            System.out.println("Problem connecting");
        }

    }

    public String randomUserID() {
        Random randomGenerator = new Random();
        return "user_" + randomGenerator.nextInt(10000);
    }

    public Country findCountryByID(List<Country> countries, String countryID) {
        return countries.stream().filter(country -> countryID.equals(country.getID())).findFirst().orElse(null);
    }

    private Color findColorByName(String colorName) {
        //https://stackoverflow.com/questions/2854043/converting-a-string-to-color-in-java
        Color color;
        try {
            Field field = Class.forName("java.awt.Color").getField(colorName);
            color = (Color) field.get(null);
        } catch (Exception e) {
            color = null; // Not defined
        }
        return color;
    }

    public static String colorName(Color c) {
        for (Field f : Color.class.getDeclaredFields()) {
            //we want to test only fields of type Color
            if (f.getType().equals(Color.class))
            try {
                if (f.get(null).equals(c)) {
                    return f.getName().toLowerCase();
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                // shouldn't not be thrown, but just in case print its stacktrace
                e.printStackTrace();
            }
        }
        return null;
    }

    private void gameTick() throws IOException {
        for (Country country : countries) {
            if (country.getIsConqueredBy() != null && country.getNStationedUnits() < Country.CONQUERED_COUTRY_GROWTH_LIMIT) {
                synchronized (countries) {
                    country.setNStationedUnits(country.getNStationedUnits() + Country.CONQUERED_COUTRY_GROWTH_RATE);
                }
                for (ClientThread client : clients) {
                    client.sendMsg("/unit " + country.getID() + " " + String.valueOf(country.getNStationedUnits()));
                }
            }
        }
    }

    public class ClientThread extends Thread {

        public Player myPlayer;
        final List<ClientThread> clients;
        final Socket socket;

        String name;
        DataOutputStream out;

        public ClientThread(List<ClientThread> clients, Socket socket, String name) {
            this.clients = clients;
            this.socket = socket;
            this.name = name;
        }

        @Override
        public void run() {
            Random randomGenerator = new Random();

            for (Country c : countries) {
                countriesByColor.put(c.getColor(), c);
            }
            try {
                System.out.println("Connection to ChatServer from "
                        + socket.getInetAddress() + ":" + socket.getPort());

                DataInputStream in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                //now that we have handled to stablish proper connection, we add ourselve into the list
                synchronized (clients) { //we must sync because other clients may be iterating over it
                    clients.add(this);
                }
                try {
                    for (String line; (line = in.readUTF()) != null;) {
                        System.out.println("RECV: " + line);
                        String[] lineParts = line.split(" ");

                        if (lineParts[0].equals("/join")) {
                            handleJoinRequest(lineParts, randomGenerator);
                        } else if (lineParts[0].equals("/exit")) {
                            handleExitGame();
                        } else if (line.contains("/select")) {
                            handleSelectRequest(lineParts);
                        } else if (lineParts[0].equals("/conquer")) {
                            handleConquerRequest(lineParts);
                        } else if (lineParts[0].equals("/send")) {
                            handleSendUnitRequest(lineParts, line);
                        }
                    }
                } catch (SocketException e) {
                    System.out.println("User " + myPlayer.getID() + ": " + myPlayer.getName() + " disconnected");
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            } finally { //we have finished or failed so let's close the socket and remove ourselves from the list
                try {
                    socket.close();
                } catch (Exception ex) {
                } //this will make sure that the socket closes
                synchronized (clients) {
                    clients.remove(this);
                }
            }
        }

        //only one thread at the time can send messages through the socket
        synchronized public void sendMsg(String msg) {
            try {
                out.writeUTF(msg);
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        private synchronized void handleJoinRequest(String[] lineParts, Random randomGenerator) throws IOException {

            Color color = findColorByName(lineParts[2]);

            myPlayer = new Player(randomUserID(), lineParts[1], color);
            Country initialCountry;
            do {                                                            //Initial country assignation
                initialCountry = countries.get(randomGenerator.nextInt(countries.size()));
            } while (initialCountry.getIsConqueredBy() != null);

            String response = "/join " + myPlayer.getID() + " " + initialCountry.getID();

            for (Player player : players) {                                 //Notifying everyone of new player
                response = response.concat("__" + player.getID() + " "
                        + player.getName() + " " + colorName(player.getColor()));

                for (Country country : player.getCountries()) {
                    response = response.concat(" " + country.getID() + "_" + country.getNStationedUnits());
                }
            }

            players.add(myPlayer);
            myPlayer.addCountry(initialCountry);
            initialCountry.setIsConqueredBy(myPlayer);
            myPlayer.setSelectedCountry(initialCountry);
            for (Player player : players) {
                System.out.println(player.getID());
            }

            System.out.println(response);
            sendMsg(response);

            for (ClientThread client : clients) {
                if (client.equals(this)) {
                    continue;
                }
                client.sendMsg("/addjoin " + myPlayer.getID() + " "
                        + myPlayer.getName() + " " + colorName(myPlayer.getColor())
                        + " " + initialCountry.getID());
            }
        }

        private synchronized void handleSelectRequest(String[] lineParts) throws IOException {
            Country country = findCountryByID(countries, lineParts[1]);
            final Player owner = country.getIsConqueredBy();
            try {

                if (owner.equals(myPlayer)) {

                    try {

                        if (myPlayer.getSelectedCountry().equals(country)) {//Deselect the country
                            myPlayer.setSelectedCountry(null);
                            sendMsg("/select NONE");
                        } else {                                            //Select new country
                            myPlayer.setSelectedCountry(country);
                            sendMsg("/select " + country.getID());
                        }
                    } catch (NullPointerException e) {                      //Select a acountry when none is
                        myPlayer.setSelectedCountry(country);
                        sendMsg("/select " + country.getID());
                    }

                } else {                                                    //Country not owned by player
                    sendMsg("/select DONT");
                }
            } catch (NullPointerException e) {                              //Country not owned
                sendMsg("/select DONT");
            }
        }

        private synchronized void handleSendUnitRequest(String[] lineParts, String line) throws IOException {
            Country destCountry = findCountryByID(countries, lineParts[2]);
            Integer nUnits = Integer.valueOf(lineParts[3]);

            if (nUnits > 0) {

                try {
                    Country srcCountry;
                    Integer unitBalance;
                    synchronized (countries) {
                        srcCountry = findCountryByID(myPlayer.getCountries(), lineParts[1]);
                        unitBalance = srcCountry.getNStationedUnits() - nUnits;
                    }

                    System.out.println(unitBalance);
                    if (unitBalance >= 0) {                                     //Enough units to send requested amount
                        srcCountry.setNStationedUnits(unitBalance);
                        for (ClientThread client : clients) {
                            if (client.equals(this)) {
                                client.sendMsg(line);
                            } else {
                                client.sendMsg("/send " + myPlayer.getID() + " "
                                        + srcCountry.getID() + " " + destCountry.getID()
                                        + " " + nUnits);
                            }
                            client.sendMsg("/unit " + srcCountry.getID() + " "
                                    + String.valueOf(unitBalance));
                        }
                    } else {                                                    //NOT enough units to send requested amount
                        sendMsg("/send NORSRC");
                    }
                } catch (NullPointerException e) {                              //No source country selected
                    sendMsg("/send NOSRC");
                }
            } else {
                sendMsg("/send NOUNTS");
            }
        }

        private synchronized void handleConquerRequest(String[] lineParts) throws IOException {
            Country destinationCountry = findCountryByID(countries, lineParts[1]);
            Integer numberOfUnits = Integer.valueOf(lineParts[2]);

            try {
                //Sending reinforcements to owned country
                if (destinationCountry.getIsConqueredBy().equals(myPlayer)) {
                    Integer unitBalance = destinationCountry.getNStationedUnits() + numberOfUnits;
                    destinationCountry.setNStationedUnits(unitBalance);
                    for (ClientThread client : clients) {
                        client.sendMsg("/unit " + destinationCountry.getID() + " " + String.valueOf(unitBalance));
                    }
                } else {                                                    //Sending units to unowned enemy country
                    Integer unitBalance = destinationCountry.getNStationedUnits() - numberOfUnits;
                    if (unitBalance < 0) {                                  //Enough units to conquer
                        Player player = destinationCountry.getIsConqueredBy();
                        player.getCountries().remove(destinationCountry);
                        if (player.getSelectedCountry().equals(destinationCountry)) {
                            player.setSelectedCountry(null);
                        }
                        if (player.getCountries().isEmpty()) {              //Player left with no countries
                            for (ClientThread client : clients) {
                                if (client.myPlayer.equals(player)) {
                                    client.sendMsg("/end");
                                }
                            }
                        }

                        destinationCountry.conquerBy(myPlayer, -unitBalance);
                        for (ClientThread client : clients) {
                            if (client.equals(this)) {
                                continue;
                            }
                            client.sendMsg("/conquer " + myPlayer.getID() + " " + destinationCountry.getID() + " " + String.valueOf(-unitBalance));
                        }

                        sendMsg("/conquer " + lineParts[1] + " " + String.valueOf(-unitBalance));
                        myPlayer.addCountry(destinationCountry);
                    } else {                                                //NOT Enough units to conquer
                        destinationCountry.setNStationedUnits(unitBalance);
                        for (ClientThread client : clients) {
                            client.sendMsg("/unit " + destinationCountry.getID() + " " + String.valueOf(unitBalance));
                        }
                    }
                }
            } catch (NullPointerException e) {                              //Sending units to unowned neutral country
                Integer unitBalance = destinationCountry.getNStationedUnits() - numberOfUnits;
                if (unitBalance < 0) {                                      //Enough units to conquer
                    sendMsg("/conquer " + lineParts[1] + " " + String.valueOf(-unitBalance));
                    myPlayer.addCountry(destinationCountry);
                    destinationCountry.conquerBy(myPlayer, -unitBalance);
                    for (ClientThread client : clients) {
                        if (client.equals(this)) {
                            continue;
                        }
                        client.sendMsg("/conquer " + myPlayer.getID() + " " + destinationCountry.getID() + " " + String.valueOf(unitBalance));
                    }
                } else {                                                    //NOT Enough units to conquer
                    destinationCountry.setNStationedUnits(unitBalance);
                    for (ClientThread client : clients) {
                        client.sendMsg("/unit " + destinationCountry.getID() + " " + String.valueOf(unitBalance));
                    }
                }
            }

            //For some reason the reference to the country object of the first client is lost in join
            //this is not true for the second but needs to be corrected
            countryReferenceCorrection();

        }

        private synchronized void handleExitGame() throws IOException {

            players.remove(myPlayer);
            for (Country country : myPlayer.getCountries()) {
                country.conquerBy(null, Country.DEFAULT_UNITS_NUMBER);
            }

            for (ClientThread client : clients) {
                if (client.equals(this)) {
                    continue;
                }
                client.sendMsg("/exit " + myPlayer.getID());
            }

            this.socket.close();

        }

        private synchronized void countryReferenceCorrection() {
            for (Country country : myPlayer.getCountries()) {

                if (findCountryByID(countries, country.getID()).getID().equals(country.getID())
                        && findCountryByID(countries, country.getID()) != country) {
                    countries.remove(findCountryByID(countries, country.getID()));
                    countries.add(country);
                }

            }
        }

    }

}
