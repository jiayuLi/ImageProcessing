import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.TreeSet;

import javax.imageio.ImageIO;


public class Tools {
	
	private static double gamma = 0.1;
	
	public static int calculateBilateralDistance (Pixel p1, Pixel p2){
		int Xdiff = p1.getX() - p2.getX();
		int Ydiff = p1.getY() - p2.getY();
		int intensityDiff = (int)(Math.pow((p1.getColor().getRed() - p2.getColor().getRed()),2) + 
						Math.pow((p1.getColor().getGreen() - p2.getColor().getGreen()),2)+
						Math.pow((p1.getColor().getBlue() - p2.getColor().getBlue()),2));
		double temp = Xdiff*Xdiff+Ydiff*Ydiff+ gamma * intensityDiff ;//+ avgTexturenessMap[p1.getY()][p1.getX()];
		
		return (int)Math.sqrt(temp);
	}
	
	public static double fractionalCalculateBilateralDistance (double x, double y, Color c, Pixel p2){
		double Xdiff = x - p2.getX();
		double Ydiff = y - p2.getY();
		int intensityDiff = (int)(Math.pow((c.getRed() - p2.getColor().getRed()),2) + 
						Math.pow((c.getGreen() - p2.getColor().getGreen()),2)+
						Math.pow((c.getBlue() - p2.getColor().getBlue()), 2));
		//System.out.println("" + avgTexturenessMap[(int)y][(int)x]);
		return Math.sqrt(Xdiff*Xdiff+Ydiff*Ydiff+ gamma * intensityDiff);// + avgTexturenessMap[(int)y][(int)x]*avgTexturenessMap[(int)y][(int)x]);
	}

	public static Pixel[][] convert2DIntArrayToPixelArray(int[][] intPixArray) {
		int h = intPixArray.length;
		int w = intPixArray[0].length;
		Pixel[][] pixelArray = new Pixel[h][w];
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				pixelArray[i][j] = new Pixel(j, i,
						new Color(intPixArray[i][j]), 999999999);
			}
		}
		return pixelArray;
	}
	
	public static int round(double d){
	    double dAbs = Math.abs(d);
	    int i = (int) dAbs;
	    double result = dAbs - (double) i;
	    if(result<0.5){
	        return d<0 ? -i : i;            
	    }else{
	        return d<0 ? -(i+1) : i+1;          
	    }
	}
	
	public static Color colorSub (Color p1, Color p2){
		int R = p1.getRed() - p2.getRed();
		if (R<0)
			R = -R;
		int G = p1.getGreen() - p2.getGreen();
		if (G<0)
			G = -G;
		int B = p1.getBlue() - p2.getBlue();
		if(B<0)
			B = -B;
		return new Color (R,G,B);
	}
	
	public static Color colorPlus (Color p1, Color p2){
		int R = p1.getRed() + p2.getRed();
		if (R>255)
			R = 255;
		int G = p1.getGreen() + p2.getGreen();
		if (G>255)
			G = 255;
		int B = p1.getBlue() + p2.getBlue();
		if(B>255)
			B = 255;
		return new Color (R,G,B);
	}

	public static int[][] convertTo2DUsingGetRGB(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[][] result = new int[height][width];

		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				result[row][col] = image.getRGB(col, row);
			}
		}

		return result;
	}

	// get int value of a color
	public static int getIntFromColor(Color c) {
		int Red = (c.getRed() << 16) & 0x00FF0000; // Shift red 16-bits and mask
													// out other stuff
		int Green = (c.getGreen() << 8) & 0x0000FF00; // Shift Green 8-bits and
														// mask out other stuff
		int Blue = c.getBlue() & 0x000000FF; // Mask out anything not blue.
		return 0xFF000000 | Red | Green | Blue; // 0xFF000000 for 100% Alpha.
												// Bitwise OR everything
												// together.
	}

	/* Return the formats sorted alphabetically and in lower case */
	public String[] getFormats() {
		String[] formats = ImageIO.getWriterFormatNames();
		TreeSet<String> formatSet = new TreeSet<String>();
		for (String s : formats) {
			formatSet.add(s.toLowerCase());
		}
		return formatSet.toArray(new String[0]);
	}

	public static BufferedImage resize(BufferedImage source, int destWidth,
			int destHeight, Object interpolation) {
		if (source == null)
			throw new NullPointerException("source image is NULL!");
		if (destWidth <= 0 && destHeight <= 0)
			throw new IllegalArgumentException(
					"destination width & height are both <=0!");
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();
		double xScale = ((double) destWidth) / (double) sourceWidth;
		double yScale = ((double) destHeight) / (double) sourceHeight;
		if (destWidth <= 0) {
			xScale = yScale;
			destWidth = (int) Math.rint(xScale * sourceWidth);
		}
		if (destHeight <= 0) {
			yScale = xScale;
			destHeight = (int) Math.rint(yScale * sourceHeight);
		}
		GraphicsConfiguration gc = getDefaultConfiguration();
		BufferedImage result = gc.createCompatibleImage(destWidth, destHeight,
				source.getColorModel().getTransparency());
		Graphics2D g2d = null;
		try {
			g2d = result.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					interpolation);
			AffineTransform at = AffineTransform.getScaleInstance(xScale,
					yScale);
			g2d.drawRenderedImage(source, at);
		} finally {
			if (g2d != null)
				g2d.dispose();
		}
		return result;
	}

	public static GraphicsConfiguration getDefaultConfiguration() {
		GraphicsEnvironment ge = GraphicsEnvironment
				.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		return gd.getDefaultConfiguration();
	}
	
	public static double intensityDiff(Color c1, Color c2){
		double dI = Math.pow( c1.getRed()-c2.getRed() , 2) + Math.pow(c1.getBlue()-c2.getBlue(), 2) + Math.pow(c1.getGreen()-c2.getGreen(), 2);
		return Math.sqrt(dI);
	}
	
	public static double intensityDiffsqrd(Color c1, Color c2){
		return (c1.getRed()-c2.getRed())*(c1.getRed()-c2.getRed()) + (c1.getGreen()-c2.getGreen())*(c1.getGreen()-c2.getGreen()) + (c1.getBlue()-c2.getBlue())*(c1.getBlue()-c2.getBlue());
		
	}
	
	
	public static double calculateS (double d, double modeM, double modeK){
		return 1/(1+Math.exp(-(modeM * (Math.abs(d)-modeK))));
	}

}
