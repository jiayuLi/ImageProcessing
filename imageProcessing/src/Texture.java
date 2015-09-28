import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;


public class Texture {
	private int width;
	private int height;
	private PseudoPixel texture [][];
	
	public Texture(String url) throws IOException{
		BufferedImage bi  = ImageIO.read(new File(url));
		width = bi.getWidth(null);
		height = bi.getHeight(null);
		texture = new PseudoPixel[height][width];
		int[][] intPixArray = GeodesicFilterV1.convertTo2DUsingGetRGB(bi);
		Pixel[][] sourcePixArray = GeodesicFilterV1.convert2DIntArrayToPixelArray(intPixArray);
		double size = width*height;
		
		double avgR = 0;
		double avgG = 0;
		double avgB = 0;
		
		for(int i=0; i<height; i++){
			for(int j=0; j<width; j++){
				avgR += sourcePixArray[i][j].getColor().getRed() / size;
				avgG += sourcePixArray[i][j].getColor().getGreen() / size;
				avgB += sourcePixArray[i][j].getColor().getBlue() / size;
			}
		}
		
		for(int i=0; i<height; i++){
			for(int j=0; j<width; j++){
				texture[i][j] = new PseudoPixel();
				texture[i][j].red = (int) (sourcePixArray[i][j].getColor().getRed() - avgR);
				texture[i][j].green = (int) (sourcePixArray[i][j].getColor().getGreen() - avgG);
				texture[i][j].blue = (int) (sourcePixArray[i][j].getColor().getBlue() - avgB);
			}
		}
		
		System.out.println(" " + url +" texture is calculated." +avgR + " " + avgG +" " + avgB );
		//System.out.println(" " + texture[50][50].red );
		
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public PseudoPixel[][] getTexture() {
		return texture;
	}

	public void setTexture(PseudoPixel[][] texture) {
		this.texture = texture;
	}
	
	
}
