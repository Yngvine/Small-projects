package game;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class MainFrame extends javax.swing.JFrame {

    ImgPanel imgPanel;
    List<Country> countries;
    Player player;
    List<Player> opponents = new ArrayList<>();
    Map<String, Country> countriesByColor = new HashMap<>();
    final List<Unit> units = new ArrayList<>();
    Thread serverListener;
    static MainFrame game;

    int startMouseX, startMouseY;

    public static String ip = "127.0.0.1";
    public static DataOutputStream out;
    public static DataInputStream in;

    public MainFrame(String playerName, String playerColorName) {
        Random randomGenerator = new Random();
        imgPanel = new ImgPanel(this);
        initComponents();
        try {
            connectWithServer();
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }

        countries = Country.readCountries("countriesWithColAndCentroids.csv");

        for (Country c : countries) {
            countriesByColor.put(c.getColor(), c);
            units.add(c.getStationedUnits());
        }

        Color playerColor = findColorByName(playerColorName);

        String response = joinGameRequest(playerName, playerColorName);

        handleJoinResponse(response, playerName, playerColor);

        //ImageUtils.calcAndSaveCentroids(countries, imgPanel.colorMaskMap, "countriesWithColAndCentroids.csv");
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                exitGameRequest();
                serverListener.stop();
                System.exit(0);
            }
        });

        sliderCustomSwitch.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                if (sliderCustomSwitch.isSelected()) {
                    sliderCustomSwitch.setText("CUSTOM");
                } else {
                    sliderCustomSwitch.setText("SLIDER");
                }
            }
        });
    }

    private void tick() {
        synchronized (units) {
            for (Unit unit : units) {
                unit.logic(countries, units);
            }
            units.removeIf(u -> u.getIsDead());
            try {
                maxNUnitsLabel.setText(String.valueOf(player.getSelectedCountry().getNStationedUnits()));
                if (!sliderCustomSwitch.isSelected()) {
                    Integer nUnits = (unitNSlider.getValue() * player.getSelectedCountry().getNStationedUnits()) / (unitNSlider.getMaximum());
                    customNUnitsField.setText(String.valueOf(nUnits));
                }
            } catch (NullPointerException e) {
                maxNUnitsLabel.setText(String.valueOf(0));
            }
        }

        imgPanel.repaint();
    }

    public static void main(String args[]) throws InterruptedException {
        String playerName = args[0];
        String playerColor = args[1];
        game = new MainFrame(playerName, playerColor);
        game.setVisible(true);
        game.setLocationRelativeTo(null);
        game.setTitle("Game a bout conquering stuff: player " + playerName);
        game.setExtendedState(JFrame.MAXIMIZED_BOTH);//Full screen

        while (true) {
            Thread.sleep(33);
            game.tick();
        }
    }

    //<editor-fold defaultstate="collapsed" desc="searchers">
    private Country findCountryByID(List<Country> countries, String countryID) {
        return countries.stream().filter(country -> countryID.equals(country.getID())).findFirst().orElse(null);
    }

    private Player findPlayerByID(List<Player> players, String playerID) {
        return players.stream().filter(player -> playerID.equals(player.getID())).findFirst().orElse(null);
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
    //</editor-fold>   

    //<editor-fold defaultstate="collapsed" desc="functions to start connection and listening of server">
    private void connectWithServer() throws IOException {
        final String hostAddr = ip;
        final int port = 12000;
        final Socket socket = new Socket(hostAddr, port);

        System.out.println("Socket connected to " + socket.getInetAddress() + ":" + socket.getPort());

        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    private void initServerResponseListener() {
        serverListener = new Thread() { //we read on a different thread to avoid getting blocked by the keyboard reading
            @Override
            public void run() {
                try {
                    while (!this.isInterrupted()) {
                        String recv = in.readUTF();

                        String[] lineParts = recv.split(" ");

                        if (lineParts[0].equals("/select")) {
                            handleSelectCountryResponse(lineParts, recv);
                        } else if (lineParts[0].equals("/conquer")) {
                            handleConquerCountryResponse(lineParts, recv);
                        } else if (lineParts[0].equals("/addjoin")) {
                            handleOpponentJoinNotification(lineParts);
                        } else if (lineParts[0].equals("/exit")) {
                            handleExitGameResponse(lineParts);
                        } else if (lineParts[0].equals("/send")) {
                            handleSendUnitsResponse(lineParts);
                        } else if (lineParts[0].equals("/unit")) {
                            handleNUnitUpdate(lineParts);
                        } else if (lineParts[0].equals("/end")) {
                            handleEndGameNotification(this);
                        }

                    }
                } catch (IOException ex) {
                    System.out.println(ex);
                }
            }
        };
        serverListener.start();
    }
//</editor-fold> 

    //<editor-fold defaultstate="collapsed" desc="functions for game start">
    private String joinGameRequest(String name, String color) {
        try {
            out.writeUTF("/join " + name + " " + color);
            String response = in.readUTF();
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    private void handleJoinResponse(String response, String playerName, Color playerColor) {
        String[] segments = response.split("__");

        String[] lineParts = segments[0].split(" ");

        if (!lineParts[0].equals("/join")) {
            System.exit(-1);
        }
        player = new Player(lineParts[1], playerName, playerColor);

        initServerResponseListener();

        Country initialCountry = findCountryByID(countries, lineParts[2]);
        player.addCountry(initialCountry);
        initialCountry.conquerBy(player, Country.DEFAULT_UNITS_NUMBER);
        imgPanel.colorCountry(initialCountry.getCenterX(), initialCountry.getCenterY(), initialCountry.getIsConqueredBy().getColor());
        player.setSelectedCountry(initialCountry);
        selectedCountryField.setText(player.getSelectedCountry().getName());

        addOpponentsAtStart(segments);
    }

    private void addOpponentsAtStart(String[] segments) {
        for (int i = 1; i < segments.length; i++) {
            String[] segmentParts = segments[i].split(" ");
            Player opponent = new Player(segmentParts[0], segmentParts[1], findColorByName(segmentParts[2]));
            opponents.add(opponent);

            for (String countryIDandUnits : Arrays.copyOfRange(segmentParts, 3, segmentParts.length)) {
                String[] countryIDandUnitsParts = countryIDandUnits.split("_");
                String countryID = countryIDandUnitsParts[0];
                Integer numberOfUnits = Integer.valueOf(countryIDandUnitsParts[1]);
                Country country = findCountryByID(countries, countryID);
                opponent.addCountry(country);
                country.conquerBy(opponent, numberOfUnits);
                imgPanel.colorCountry(country.getCenterX(), country.getCenterY(), country.getIsConqueredBy().getColor());
            }
        }
    }

    private void handleOpponentJoinNotification(String[] lineParts) {
        Country country = findCountryByID(countries, lineParts[4]);
        Player opponent = new Player(lineParts[1], lineParts[2],
                findColorByName(lineParts[3]), country);
        opponents.add(opponent);
        country.conquerBy(opponent, Country.DEFAULT_UNITS_NUMBER);
        imgPanel.colorCountry(country.getCenterX(), country.getCenterY(), country.getIsConqueredBy().getColor());
    }
    //</editor-fold>     

    //<editor-fold defaultstate="collapsed" desc="functions for country selection/desection">
    private void selectCountryRequest(String country) {
        try {
            out.writeUTF("/select " + country);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSelectCountryResponse(String[] lineParts, String recv) {
        switch (lineParts[1]) {
            case "NONE":
                player.setSelectedCountry(null);
                selectedCountryField.setText("NONE");
                break;
            case "DONT":
                System.out.println("You don't own this country");
                break;
            default:
                Country country = findCountryByID(countries, lineParts[1]);
                if (country == null) {
                    System.out.println("ERROR: unknown server response " + recv);
                } else {
                    player.setSelectedCountry(country);
                    selectedCountryField.setText(country.getName());
                }
                break;
        }
    }
    //</editor-fold>     

    //<editor-fold defaultstate="collapsed" desc="functions for unit management">
    private void sendUnitsRequest(final Country country) {
        try {
            try {
                Integer nUnits;
                if (sliderCustomSwitch.isSelected()) {
                    nUnits = Integer.valueOf(customNUnitsField.getText());
                } else {
                    nUnits = (unitNSlider.getValue() * player.getSelectedCountry().getNStationedUnits()) / (unitNSlider.getMaximum());
                }
                out.writeUTF("/send " + player.getSelectedCountry().getID() + " " + country.getID() + " " + nUnits);
            } catch (NullPointerException e) {
                System.out.println("Select a country first");
            }
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void handleSendUnitsResponse(String[] lineParts) {
        if (lineParts.length == 4) {

            Country srcCountry = findCountryByID(countries, lineParts[1]);
            Country destCountry = findCountryByID(countries, lineParts[2]);
            Unit unit = new Unit(srcCountry.getCenterX(), srcCountry.getCenterY(), destCountry, player, "dynamic", Integer.valueOf(lineParts[3]));

            synchronized (units) {
                units.add(unit);
            }
        } else if (lineParts.length == 5) {
            Player opponent = findPlayerByID(opponents, lineParts[1]);
            Country srcCountry = findCountryByID(countries, lineParts[2]);
            Country destCountry = findCountryByID(countries, lineParts[3]);
            Unit unit = new Unit(srcCountry.getCenterX(), srcCountry.getCenterY(), destCountry, opponent, "void", Integer.valueOf(lineParts[4]));
            synchronized (units) {
                units.add(unit);
            }
        } else {
            if (lineParts[1].equals("NOSRC")) {
                System.out.println("No country selected");
            } else if (lineParts[1].equals("NORSRC")) {
                System.out.println("Not enough troops");
            } else if (lineParts[1].equals("NOUNTS")) {
                System.out.println("Cant send 0 units");
            }
        }
    }

    private void handleNUnitUpdate(String[] lineParts) throws NumberFormatException {
        Country country = findCountryByID(countries, lineParts[1]);
        Integer nUnit = Integer.valueOf(lineParts[2]);
        country.setNStationedUnits(nUnit);
    }
    //</editor-fold>    

    //<editor-fold defaultstate="collapsed" desc="functions for country conquest">
    public static void conquerCountryRequest(String destCountry, int nUnits) {
        try {
            out.writeUTF("/conquer " + destCountry + " " + nUnits);
        } catch (Exception e) {
        }
    }

    private void handleConquerCountryResponse(String[] lineParts, String recv) {
        if (lineParts.length == 3) {
            switch (lineParts[1]) {
                case "NOSOURCE":
                    System.out.println("Please select firsty a country to invade from");
                    break;
                default:
                    Integer numberOfUnits = Integer.valueOf(lineParts[2]);
                    Country country = findCountryByID(countries, lineParts[1]);
                    if (country == null) {
                        System.out.println("ERROR: unknown server response " + recv);
                    } else {
                        try {
                            Player opponent = country.getIsConqueredBy();
                            opponent.getCountries().remove(country);
                            player.addCountry(country);
                            country.conquerBy(player, numberOfUnits);
                            imgPanel.colorCountry(country.getCenterX(), country.getCenterY(), country.getIsConqueredBy().getColor());
                        } catch (NullPointerException e) {
                            player.addCountry(country);
                            country.conquerBy(player, numberOfUnits);
                            imgPanel.colorCountry(country.getCenterX(), country.getCenterY(), country.getIsConqueredBy().getColor());
                        }
                    }
                    break;
            }
        } else if (lineParts.length == 4) {
            Integer numberOfUnits = Integer.valueOf(lineParts[3]);
            Player opponent = findPlayerByID(opponents, lineParts[1]);
            Country country = findCountryByID(countries, lineParts[2]);
            if (player.getCountries().contains(country)) {
                player.getCountries().remove(country);
                try {
                    if (player.getSelectedCountry().equals(country)) {
                        player.setSelectedCountry(null);
                        selectedCountryField.setText("NONE");
                    }
                } catch (NullPointerException e) {

                }
            }
            try {
                opponent.addCountry(country);
                country.conquerBy(opponent, numberOfUnits);
                imgPanel.colorCountry(country.getCenterX(), country.getCenterY(), country.getIsConqueredBy().getColor());
            } catch (NullPointerException e) {
                System.out.println("Invalid player or country identifier\nReceived: " + recv);
            }
        } else {
            System.out.println("ERROR: unknown server response " + recv);
        }
    }
    //</editor-fold>  

    //<editor-fold defaultstate="collapsed" desc="functions for game exit">
    private void exitGameRequest() {
        try {
            out.writeUTF("/exit");
        } catch (Exception e) {
        }
    }

    private void handleExitGameResponse(String[] lineParts) {
        Player opponent = findPlayerByID(opponents, lineParts[1]);
        for (Country country : opponent.getCountries()) {
            country.conquerBy(null, Country.DEFAULT_UNITS_NUMBER);
            float[] defaultColor = Color.RGBtoHSB(209, 219, 221, null);
            imgPanel.colorCountry(country.getCenterX(), country.getCenterY(), Color.getHSBColor(defaultColor[0], defaultColor[1], defaultColor[2]));
        }
    }

    private void handleEndGameNotification(Thread thread) {
        (new Thread() {
            @Override
            public void run() {
                EndgameWindow.main(null);
            }
        }).start();
        exitGameRequest();
        game.dispose();
        thread.stop();
    }
//</editor-fold>      

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mapPanelGame = imgPanel;
        selectedCountryField = new javax.swing.JTextField();
        unitNSlider = new javax.swing.JSlider();
        minNUnitsLabel = new javax.swing.JLabel();
        maxNUnitsLabel = new javax.swing.JLabel();
        selectedCountryLabel = new javax.swing.JLabel();
        sliderCustomSwitch = new javax.swing.JToggleButton();
        customNUnitsField = new javax.swing.JTextField();
        nUnitsField = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(255, 255, 255));

        mapPanelGame.setBackground(new java.awt.Color(255, 255, 255));
        mapPanelGame.setPreferredSize(new java.awt.Dimension(1920, 1080));
        mapPanelGame.setRequestFocusEnabled(false);
        mapPanelGame.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                mapPanelGameMouseDragged(evt);
            }
        });
        mapPanelGame.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                mapPanelGameMouseWheelMoved(evt);
            }
        });
        mapPanelGame.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                mapPanelGameMouseClicked(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                mapPanelGameMousePressed(evt);
            }
        });

        javax.swing.GroupLayout mapPanelGameLayout = new javax.swing.GroupLayout(mapPanelGame);
        mapPanelGame.setLayout(mapPanelGameLayout);
        mapPanelGameLayout.setHorizontalGroup(
            mapPanelGameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1920, Short.MAX_VALUE)
        );
        mapPanelGameLayout.setVerticalGroup(
            mapPanelGameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1080, Short.MAX_VALUE)
        );

        selectedCountryField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectedCountryFieldActionPerformed(evt);
            }
        });

        minNUnitsLabel.setText("0");

        maxNUnitsLabel.setText("0");

        selectedCountryLabel.setText("Selected country:");

        sliderCustomSwitch.setText("SLIDER");
        sliderCustomSwitch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sliderCustomSwitchActionPerformed(evt);
            }
        });

        customNUnitsField.setText("0");
        customNUnitsField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                customNUnitsFieldActionPerformed(evt);
            }
        });

        nUnitsField.setText("Numer of units:");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mapPanelGame, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(45, 45, 45)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(selectedCountryLabel)
                    .addComponent(selectedCountryField, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(210, 210, 210)
                        .addComponent(unitNSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(45, 45, 45)
                        .addComponent(sliderCustomSwitch))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(220, 220, 220)
                        .addComponent(minNUnitsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(151, 151, 151)
                        .addComponent(maxNUnitsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(31, 31, 31)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(customNUnitsField))
                    .addComponent(nUnitsField))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(selectedCountryLabel)
                        .addGap(8, 8, 8))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(minNUnitsLabel)
                            .addComponent(maxNUnitsLabel)
                            .addComponent(nUnitsField))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(selectedCountryField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(unitNSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sliderCustomSwitch)
                    .addComponent(customNUnitsField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(mapPanelGame, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void mapPanelGameMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapPanelGameMouseClicked

        int mapX = imgPanel.screenToMapX(evt.getX());
        int mapY = imgPanel.screenToMapY(evt.getY());

        final int color = imgPanel.clickAt(mapX, mapY);

        final String strColor = String.format("#%06x", 0xFFFFFF & color);
        final Country country = countriesByColor.get(strColor);

        if (country == null) {
            return;
        }

        if (evt.getButton() == 1) {

            selectCountryRequest(country.getID());

        } else if (evt.getButton() == 3) {
            sendUnitsRequest(country);
        }
        imgPanel.repaint();
    }//GEN-LAST:event_mapPanelGameMouseClicked

    private void mapPanelGameMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapPanelGameMouseDragged
        final int diffX = startMouseX - evt.getX();
        final int diffY = startMouseY - evt.getY();

        imgPanel.scroll(diffX, diffY);

        startMouseX = evt.getX();
        startMouseY = evt.getY();
        imgPanel.repaint();
    }//GEN-LAST:event_mapPanelGameMouseDragged

    private void mapPanelGameMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_mapPanelGameMouseWheelMoved
        imgPanel.zoomAt(evt.getX(), evt.getY(), evt.getPreciseWheelRotation());
        imgPanel.repaint();
    }//GEN-LAST:event_mapPanelGameMouseWheelMoved

    private void mapPanelGameMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_mapPanelGameMousePressed
        startMouseX = evt.getX();
        startMouseY = evt.getY();
    }//GEN-LAST:event_mapPanelGameMousePressed

    private void selectedCountryFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectedCountryFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_selectedCountryFieldActionPerformed

    private void sliderCustomSwitchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sliderCustomSwitchActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sliderCustomSwitchActionPerformed

    private void customNUnitsFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customNUnitsFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_customNUnitsFieldActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField customNUnitsField;
    private javax.swing.JPanel mapPanelGame;
    private javax.swing.JLabel maxNUnitsLabel;
    private javax.swing.JLabel minNUnitsLabel;
    private javax.swing.JLabel nUnitsField;
    private javax.swing.JTextField selectedCountryField;
    private javax.swing.JLabel selectedCountryLabel;
    private javax.swing.JToggleButton sliderCustomSwitch;
    private javax.swing.JSlider unitNSlider;
    // End of variables declaration//GEN-END:variables

}
