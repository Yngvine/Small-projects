package game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Country {
    
    public static final int DEFAULT_UNITS_NUMBER = 20;
    public static final int CONQUERED_COUTRY_GROWTH_RATE = 5;
    public static final int CONQUERED_COUTRY_GROWTH_LIMIT = 150;

    private String name;
    private String color;
    private String officialName;
    private String ID;
    private String flagPath;
    private Unit stationedUnits;
    
    private int centerX, centerY;
    private Player isConqueredBy;
    
    //<editor-fold defaultstate="collapsed" desc="getters setters">
    
    public Unit getStationedUnits() {
        return stationedUnits;
    }

    public Integer getNStationedUnits() {
        return stationedUnits.getNUnits();
    }

    public void setNStationedUnits(Integer stationedUnits) {
        this.stationedUnits.setNUnits(stationedUnits);
    }
    
    public String getFlagPath() {
        return flagPath;
    }
    
    public void setFlagPath(String flagPath) {
        this.flagPath = flagPath;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String color) {
        this.color = color;
    }
    
    public String getOfficialName() {
        return officialName;
    }
    
    public void setOfficialName(String officialName) {
        this.officialName = officialName;
    }
    
    public String getID() {
        return ID;
    }
    
    public void setID(String ID) {
        this.ID = ID;
    }
    
    public int getCenterX() {
        return centerX;
    }
    
    public void setCenterX(int centerX) {
        this.centerX = centerX;
    }
    
    public int getCenterY() {
        return centerY;
    }
    
    public void setCenterY(int centerY) {
        this.centerY = centerY;
    }
    
    public Player getIsConqueredBy() {
        return isConqueredBy;
    }
    
    public void setIsConqueredBy(Player conqueredBy) {
        this.isConqueredBy = conqueredBy;
    }
    
    public void conquerBy(Player conqueredBy, Integer nUnits) {
        this.isConqueredBy = conqueredBy;
        this.stationedUnits.setNUnits(nUnits);
    }
//</editor-fold>
    
    public Country(String name, String officialName, String ID) {
        this.name = name;
        this.ID = ID;
        this.officialName = officialName;
        this.flagPath = "flags/" + ID + ".png";
        this.stationedUnits = new Unit(this.centerX, this.centerY, this, this.isConqueredBy,"static", DEFAULT_UNITS_NUMBER);
    }
    
    public static List<Country> readCountries(String fileName) {
        List<Country> countries = new ArrayList<>();

        try (BufferedReader br
                = new BufferedReader(new FileReader(fileName))) {

            for (String line; (line = br.readLine()) != null;) {

                line = line.replace("\"", "");

                String[] parts = line.split(",");
                Country c = new Country(
                        parts[0],
                        parts[1],
                        parts[2]);

                if (parts.length > 3)
                    c.setColor( parts[3] );
                if (parts.length > 4)
                    c.setCenterX( Integer.parseInt( parts[4] ) );
                if (parts.length > 5)
                    c.setCenterY( Integer.parseInt( parts[5] ) );
                
                countries.add(c);

            }

        } catch (IOException ex) {
            Logger.getLogger(Country.class.getName()).log(Level.SEVERE, null, ex);
        }
        return countries;

    }

    public static void writeCountries(String filename, List<Country> countries){
        try ( FileWriter f = new FileWriter(filename); ) {
            
            for (Country c : countries) {
                f.write(c.getName() + "," + c.getOfficialName() + "," + c.getID() + "," 
                        + c.getColor() + "," 
                        + c.getCenterX() + "," + c.getCenterY() + "\n");
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
