/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package game;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author alumno
 */
public class Player {
    private String id;
    private String name;
    private Color color;
    private List<Country> countries = new ArrayList<>();;
    private Country selectedCountry;

    public Player(String name, Color color, Country country) {
        this.name = name;
        this.color = color;
        this.countries.add(country);
    }
    
    public Player(String id, String name, Color color, Country country) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.countries.add(country);
    }
    
    public Player(String id, String name, Color color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
    
    public Player(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public Country getSelectedCountry() {
        return selectedCountry;
    }

    public void setSelectedCountry(Country selectedCountry) {
        this.selectedCountry = selectedCountry;
    }

    public String getID() {
        return id;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public List<Country> getCountries() {
        return countries;
    }

    public void addCountry(Country country) {
        this.countries.add(country);
    }
}
