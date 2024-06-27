package game;

import java.awt.Color;
import java.awt.Graphics2D;
import static java.lang.Math.max;
import java.util.List;

/**
 *
 * @author asier.marzo
 */
public class Unit {

    public static final int SIZE = 10;
    public static final double SPEED = 5;

    private double x, y;
    private Country target;
    private Player player;
    /*3 possible values for type:
        ·static: used to paint countries names and number of units
        ·dynamic: used to invade or supply countries, have active 
                  communication with server
        ·void: used to represend the movement of enemy units, do not
               have communication with server
     */
    private final String type;

    private Integer nUnits;
    private Boolean isDead;

    public Unit(int x, int y, Country target, Player player, String type) {
        this.x = x;
        this.y = y;
        this.target = target;
        this.player = player;
        this.type = type;
        this.isDead = false;
    }

    public Unit(int x, int y, Country target, Player player, String type, Integer nUnits) {
        this.x = x;
        this.y = y;
        this.target = target;
        this.player = player;
        this.type = type;
        this.isDead = false;
        this.nUnits = nUnits;
    }

    //<editor-fold defaultstate="collapsed" desc="getters setters">
    public Integer getNUnits() {
        return nUnits;
    }

    public void setNUnits(Integer nUnits) {
        this.nUnits = nUnits;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public Country getTargetCountry() {
        return target;
    }

    public void setTargetCountry(Country target) {
        this.target = target;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Boolean getIsDead() {
        return isDead;
    }

    public void setIsDead(Boolean isDead) {
        this.isDead = isDead;
    }
    //</editor-fold>

    void logic(List<Country> countries, List<Unit> units) {
        switch (type) {
            case "static":
                x = target.getCenterX();
                y = target.getCenterY();
                break;

            case "dynamic": {
                if (target == null) {
                    return;
                }
                final double diffX = target.getCenterX() - x;
                final double diffY = target.getCenterY() - y;
                final double dist = Math.sqrt(diffX * diffX + diffY * diffY);
                if (dist < SPEED) {
                    x = target.getCenterX();
                    y = target.getCenterY();
                    MainFrame.conquerCountryRequest(target.getID(), nUnits);
                    isDead = true;
                } else {
                    x += diffX / dist * SPEED;
                    y += diffY / dist * SPEED;
                }
                break;
            }

            case "void": {
                if (target == null) {
                    return;
                }
                final double diffX = target.getCenterX() - x;
                final double diffY = target.getCenterY() - y;
                final double dist = Math.sqrt(diffX * diffX + diffY * diffY);
                if (dist < SPEED) {
                    x = target.getCenterX();
                    y = target.getCenterY();
                    isDead = true;

                } else {
                    x += diffX / dist * SPEED;
                    y += diffY / dist * SPEED;
                }
                break;
            }

            default:
                break;
        }
    }

    void draw(Graphics2D g2, ImgPanel imgPanel) {
        final int x = imgPanel.mapToScreenX(getX());
        final int y = imgPanel.mapToScreenY(getY());
        switch (type) {
            case "static":
                g2.setColor(Color.BLUE);
                g2.fillOval(x - Unit.SIZE / 2, y - Unit.SIZE / 2, Unit.SIZE, Unit.SIZE);
                if (getTargetCountry() != null) {
                    g2.setColor(Color.MAGENTA);
                    g2.drawString(String.valueOf(nUnits), x - (String.valueOf(nUnits).length() / 2) * 7, y + (Unit.SIZE / 2) * 3);
                    g2.setColor(Color.BLACK);
                    g2.drawString(getTargetCountry().getName(), x - (String.valueOf(getTargetCountry().getName()).length() / 2) * 6, y - (Unit.SIZE / 2));
                }
                break;

            case "dynamic":
                g2.setColor(player.getColor());
                g2.fillOval(x - Unit.SIZE / 2, y - Unit.SIZE / 2, Unit.SIZE, Unit.SIZE);
                if (getTargetCountry() != null) {
                    g2.setColor(Color.MAGENTA);
                    g2.drawString(String.valueOf(nUnits), x - (max(String.valueOf(nUnits).length() / 2, 1)) * 6, y - (Unit.SIZE / 2));
                }
                break;

            case "void":
                g2.setColor(player.getColor());
                g2.fillOval(x - Unit.SIZE / 2, y - Unit.SIZE / 2, Unit.SIZE, Unit.SIZE);
                if (getTargetCountry() != null) {
                    g2.setColor(Color.MAGENTA);
                    g2.drawString(String.valueOf(nUnits), x - (max(String.valueOf(nUnits).length() / 2, 1)) * 6, y - (Unit.SIZE / 2));
                }
                break;

            default:
                break;
        }
    }
}
