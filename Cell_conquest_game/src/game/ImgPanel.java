
package game;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class ImgPanel extends JPanel {
    final MainFrame game;
    BufferedImage mapImage;
    BufferedImage colorMaskMap;
    
    double zoomLevel = 0.5;
    double shiftX = 0, shiftY = 0;
    
    
    public ImgPanel(MainFrame game) {
        this.game = game;
        try {
            this.mapImage = ImageIO.read(new File("voidMap.png"));
            this.colorMaskMap = ImageIO.read(new File("map.png"));
        } catch (IOException ex) {
            Logger.getLogger(ImgPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }  


    void scroll(int diffX, int diffY) {
        shiftX -= diffX / zoomLevel;
        shiftY -= diffY / zoomLevel;
    }

    void zoomAt(int mx, int my, double preciseWheelRotation) {
        final int prevX = screenToMapX(mx);
        final int prevY = screenToMapY(my);
        
        zoomLevel -= preciseWheelRotation/16.0;
        zoomLevel = clamp(zoomLevel, 0.2, 2);
        
        final int x = screenToMapX(mx);
        final int y = screenToMapY(my);
        
        shiftX += (x - prevX);
        shiftY += (y - prevY);
    }
    
    public int screenToMapX( double xScreen ){
        return (int) (xScreen/zoomLevel - shiftX);
    }
    public int screenToMapY( double yScreen ){
        return (int) (yScreen/zoomLevel - shiftY);
    }
    public int mapToScreenX( double xMap ){
        return (int) ( (xMap + shiftX) * zoomLevel);
    }
    public int mapToScreenY( double yMap ){
        return (int) ( (yMap + shiftY) * zoomLevel);
    }
 
    @Override
    public void paint(Graphics g) {
        final int w = getWidth();
        final int h = getHeight();
        
        final Graphics2D g2 = (Graphics2D) g;
        g2.clearRect(0, 0, w, h);
        
        g2.drawImage(mapImage, (int)(shiftX*zoomLevel), (int)(shiftY*zoomLevel), 
                (int)(mapImage.getWidth()*zoomLevel), 
                (int)(mapImage.getHeight()*zoomLevel), null); 
        
         
        
        synchronized(game.units){
            for (Unit unit : game.units) {
                unit.draw(g2, this);
            }
        }
        Toolkit.getDefaultToolkit().sync();
    }   
    
    public static double clamp(double value, double min, double max){
        return Math.max(min, Math.min(value, max));
    }

    int clickAt(int mapX, int mapY) {
        final int color = colorMaskMap.getRGB(mapX, mapY);
        //mapImage.setRGB(mapX, mapY, Color.RED.getRGB());
        
        return color;
    }

    void colorCountry(int mapX, int mapY, Color targetColor) {
        final int originalColor = mapImage.getRGB(mapX, mapY);
        ImageUtils.floodFill(mapImage, mapX, mapY, originalColor, targetColor.getRGB());
    }

    void paintNameAtCentroids(List<Country> countries) {
        final Graphics g = mapImage.getGraphics();
        g.setColor(Color.BLACK);
        for (Country c : countries) {
            g.drawString(c.getName(), c.getCenterX(), c.getCenterY());
        }
        
        g.dispose();
    }
}
