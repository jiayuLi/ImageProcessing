

import java.io.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeSet;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.*;

import javax.imageio.*;
import javax.swing.*;

public class BilateralVariation extends Component implements ActionListener {
	
	String descs[] = { "Original", "bicubic upsampled", "2nd pass + texture enriched",
			"bicubic upsampling + bilateral variation", "bilateral variation", "Diff between Bilateral and Geodesic",
			"upsampling + bilateral + fractional original", "upsampling + randomize texture + bilateral", "cross filter with original"
			, "edge detection + collage", "textureness map", "FAILED ATTEMPT: upsampling + fractional original + weight redistributed", "lightlighter/darkdarker"
			, "nearest neighbour upsampling + bilateral", "additive edge penalize ", "I'=I+d*S(d)"
			, "contrast enhancement", "spring mass", "1D spring mass"};

	//static String IMAGESOURCE = "texim/dragonfly.png";//dragonfly0.png
	static String IMAGESOURCE = "redpicnictable.jpg";
	static String ORIGINALIMAGE = "redpicnictable.jpg"; //used for cross-filtering with original image

	//final int maskSize = 200;

	static double maskSize = 30;
	static int regionWH = (int) Math.sqrt(maskSize*16); //width or height of the sample region
	//final int upsampleMaskSize = 10;
	final double R = 1; //for geodesic
	static double gamma = 0.1; // for bilateral, for weight on intensity diff
	static double resizeScale =3;
	final int randomRange = 15;
	//final double exponentialPower = 7;
	//final int numOfPotEntrance = 10;
	//final int incrementalWeightInPot = 0;
	//final int potEntranceDensity = 25; //higher - less dense
	//final Color potPaintColor = Color.BLACK;
	
	final static LinkedList<Line> lines = new LinkedList<Line>(); //for histogram analysis
	final static LinkedList<Point> points = new LinkedList<Point>(); //for scatterplot analysis
	static int histogram[] = new int[256];
	static int scatterPlot[][] = new int[256][360];
	static int logKernel[][];
	static int MoG[][];
	static boolean MoGisOn = false;
	static int avgMog = 0;
	static double alpha = 0.5;
	final static Color histogramColor = Color.RED;
	final int histogramStartX = 1325;
	final int histogramStartY = 80;
	final int MASKBACKGROUND = 65280; // red = 16711680  green = 65280 blue = 255 Grey = 3289650
	
	final double modeM = 0.5;
	final int modeK = 5;
	
	final double textureIntensity = 0.2; //define how much texture is added
	final int texturenessStrength = 500; //for collage
	final double LLDDdelta = 0.99;
	private static double avgTexturenessMap[][];
	
	//for mass-spring
	final static double springK = 1;
	final static double damperC = 0.5;
	final int TIMESTEPS = 500;
	static boolean circleIsOn = false;
	static boolean showOriginalGrid = true;
	static boolean redoCalcFlag = false;
	static int offset1D = 0;
	static Pixel[][] sourcePixArrayFor1D;

	static int opIndex;
	static int lastOp;
	private static BufferedImage bi;

	private static BufferedImage biFiltered;

	private BufferedImage background;

	private BufferedImage biOrig;
	int w, h;
	static JFrame f;
	static JPanel p1;
	static int mouseX = 0;
	static int mouseY = 0;

	static JCheckBox jcb = new JCheckBox("show mask");
	static JTextField jtf = new JTextField(
			"Enter Image Url to load another image", 20);
	static JTextField jtfMaskSize = new JTextField(""+maskSize, 3);
	static JTextField jtfScale = new JTextField(""+resizeScale, 3);
	static JTextField jtfGamma = new JTextField(""+gamma, 3);
	static JTextField jtfAlpha = new JTextField(""+alpha, 3);
	static JButton load = new JButton("Load");
	static JButton update = new JButton("Update");
	
	//static JTextField jtf_InpotPixCount = new JTextField("Pix#InPot", 10);

	public static final float[] SHARPEN3x3 = { // sharpening filter kernel
	0.f, -1.f, 0.f, -1.f, 5.f, -1.f, 0.f, -1.f, 0.f };

	public static final float[] BLUR3x3 = { 0.1f, 0.1f, 0.1f, // low-pass filter
																// kernel
			0.1f, 0.2f, 0.1f, 0.1f, 0.1f, 0.1f };

	public BilateralVariation() {
		try {
			bi = ImageIO.read(new File(IMAGESOURCE));
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			if (bi.getType() != BufferedImage.TYPE_INT_RGB) {
				BufferedImage bi2 = new BufferedImage(w, h,
						BufferedImage.TYPE_INT_RGB);
				Graphics big = bi2.getGraphics();
				big.drawImage(bi, 0, 0, null);
				if(biOrig!=null)
					big.drawImage(biOrig, 800, 0, null);
				biFiltered = bi = bi2;
			}
		} catch (IOException e) {
			System.out.println("Image could not be read");
			System.exit(1);
		}
	}

	public Dimension getPreferredSize() {
		return new Dimension(w, h);
	}

	String[] getDescriptions() {
		return descs;
	}

	void setOpIndex(int i) {
		opIndex = i;
	}

	public void paint(Graphics g) {
		try {
			filterImage();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		g.drawImage(biFiltered, 0, 0, null);
		if(biOrig!=null){
			g.drawImage(biOrig, 0, 0, null);
			biOrig = null;
		}
		if(jcb.isSelected()){
			g.setColor(Color.white);
			g.fillRect(histogramStartX-5, histogramStartY-80, 275, 635);
			g.setColor(Color.black);
			g.drawString("0", histogramStartX, histogramStartY+10);
			g.drawString("256", histogramStartX+250, histogramStartY+10);
			g.drawString("blue: input", histogramStartX+10, histogramStartY-65);
			g.drawString("green: output", histogramStartX+10, histogramStartY-50);
			g.drawString("red: mode", histogramStartX+10, histogramStartY-35);
			g.drawString("closest outside-mask pixels", histogramStartX+10, histogramStartY+35);
			if(mouseX!=0 || mouseY!=0){
				g.setColor(Color.red);
				/*g.drawLine(mouseX+10, mouseY, mouseX+50, mouseY);
				g.drawLine(mouseX-10, mouseY, 0, mouseY);
				g.drawLine(mouseX, mouseY-10, mouseX, mouseY-50);
				g.drawLine(mouseX, mouseY+10, mouseX, mouseY+50);*/
				g.setColor(Color.black);
			}
			g.setColor(Color.black);
			g.drawLine(histogramStartX, histogramStartY+400, histogramStartX, histogramStartY+40);
			g.drawLine(histogramStartX, histogramStartY+220, histogramStartX+300, histogramStartY+220);
			g.drawString("HUE", histogramStartX, histogramStartY+395);
			g.drawString("LIGHTNESS", histogramStartX+210, histogramStartY+235);
			g.drawString("AvgMoG: "+avgMog + " ", histogramStartX, histogramStartY+415);
		}
		for (Line line : lines) {
	        g.setColor(line.color);
	        g.drawLine(line.x1, line.y1, line.x2, line.y2);
	    }
		
		for (Point point:points){
			g.setColor(point.color);
			g.fillOval(point.x-point.rad/2, point.y-point.rad/2, point.rad, point.rad);
		}
		
		if(opIndex==18){
			g.drawString("black: original  red:current", histogramStartX+10, histogramStartY+720);
		}
	}

	

	public void filterImage() throws IOException {
		BufferedImageOp op = null;

		if (jcb.isSelected()) {
			
		} else {
			if (opIndex == lastOp) {
				return;
			}
			lastOp = opIndex;
		}

		switch (opIndex) {

		case 0:
			bi = ImageIO.read(new File(IMAGESOURCE));
			biFiltered = bi; /* original */
			//addLine(50,50,1000,1000);
			//repaint();
			return;

		case 1: /*bicubic upsampled */{
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			break;
		}
			
		case 2: /* 2nd pass + texture enriched */{
			
			Texture t2 = new Texture("textures/cemete-texture.jpg");
			Texture t3 = new Texture("textures/plate.jpg");
			Texture t5 = new Texture("textures/grass.jpg");
			Texture t1 = new Texture("textures/gravel.jpg");
			Texture t4 = new Texture("textures/towel.jpg");
			
			/*Texture t2 = new Texture("textures/blondhair.jpg");
			Texture t3 = new Texture("textures/hay.jpg");
			Texture t5 = new Texture("textures/fur.jpg");
			Texture t1 = new Texture("textures/grass.jpg");
			Texture t4 = new Texture("textures/paintwall.jpg");
			*/
			//Texture t1 = new Texture("textures/plate.jpg");
			//Texture t2 = t1;
			//Texture t3 = t1;
			//Texture t4 = t1;
			//Texture t5 = t1;
			//
			
			biFiltered = ImageIO.read(new File(IMAGESOURCE)); //upsampled 2nd pass
			w = biFiltered.getWidth(null);
			h = biFiltered.getHeight(null);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			/*bi = ImageIO.read(new File(ORIGINALIMAGE));
			int[][] origIntPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] origSourcePixArray = convert2DIntArrayToPixelArray(origIntPixArray);
			int origW = bi.getWidth(null);
			int origH = bi.getHeight(null);*/
			
			double tempIntensity = 0.0;
			double deltaR = 0.0;
			double deltaG = 0.0;
			double deltaB = 0.0;
			for(int i=0; i<h; i++){
				for(int j=0; j<w; j++){
					
					deltaR = 0.0;
					deltaG = 0.0;
					deltaB = 0.0;
					tempIntensity = sourcePixArray[i][j].getIntensity();
					if(tempIntensity<128){
						deltaR += t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].red * ((128-tempIntensity)/128);
						deltaG += t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].green * ((128-tempIntensity)/128);
						deltaB += t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].blue * ((128-tempIntensity)/128);
					}
					
					if(tempIntensity<192){
						deltaR += t2.getTexture()[i%t2.getHeight()][j%t2.getWidth()].red * ((128-Math.abs(64-tempIntensity))/128);
						deltaG += t2.getTexture()[i%t2.getHeight()][j%t2.getWidth()].green * ((128-Math.abs(64-tempIntensity))/128);
						deltaB += t2.getTexture()[i%t2.getHeight()][j%t2.getWidth()].blue * ((128-Math.abs(64-tempIntensity))/128);
					}
					
					if(tempIntensity<256){
						deltaR += t3.getTexture()[i%t3.getHeight()][j%t3.getWidth()].red * ((128-Math.abs(128-tempIntensity))/128);
						deltaG += t3.getTexture()[i%t3.getHeight()][j%t3.getWidth()].green * ((128-Math.abs(128-tempIntensity))/128);
						deltaB += t3.getTexture()[i%t3.getHeight()][j%t3.getWidth()].blue * ((128-Math.abs(128-tempIntensity))/128);
					}
					
					if(tempIntensity>=64){
						deltaR += t4.getTexture()[i%t4.getHeight()][j%t4.getWidth()].red * ((128-Math.abs(192-tempIntensity))/128);
						deltaG += t4.getTexture()[i%t4.getHeight()][j%t4.getWidth()].green * ((128-Math.abs(192-tempIntensity))/128);
						deltaB += t4.getTexture()[i%t4.getHeight()][j%t4.getWidth()].blue * ((128-Math.abs(192-tempIntensity))/128);
					}
					if(tempIntensity>=128){
						deltaR += t5.getTexture()[i%t5.getHeight()][j%t5.getWidth()].red * ((128-Math.abs(255-tempIntensity))/128);
						deltaG += t5.getTexture()[i%t5.getHeight()][j%t5.getWidth()].green * ((128-Math.abs(255-tempIntensity))/128);
						deltaB += t5.getTexture()[i%t5.getHeight()][j%t5.getWidth()].blue * ((128-Math.abs(255-tempIntensity))/128);
					}
					
					
					/*
					//varied texture intensity, based on original image
					int o = (int) (i/resizeScale);
					int p =  (int) (j/resizeScale);
					Color c = origSourcePixArray[o][p].getColor();
					double textureness = 0.0;
					int neighbourPixCount = 0;
					Color temp = new Color(0);
					if(p>0){
						temp = origSourcePixArray[o][p-1].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(p<(origW-1)){
						temp = origSourcePixArray[o][p+1].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(o>0){
						temp = origSourcePixArray[o-1][p].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(o<(origH-1)){
						temp = origSourcePixArray[o+1][p].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					
					textureness/=neighbourPixCount;
					textureness/=196608; //(256^2)*3
					textureness*=100;
					//System.out.println(""+textureness);
					
					deltaR *= textureness;
					deltaG *= textureness;
					deltaB *= textureness;
					*/
					
					//fixed texture intensity
					//deltaR *= textureIntensity;
					//deltaG *= textureIntensity;
					//deltaB *= textureIntensity;
					
					//according to texture map
					
					
					/*deltaR *= ((255 - avgTexturenessMap[i][j]) /512) ;
					deltaG *= ((255 - avgTexturenessMap[i][j]) /512 );
					deltaB *= ((255 - avgTexturenessMap[i][j]) /512) ;*/
					
					double textureness  = 0.2;
					
					deltaR *= textureness;
					deltaG *= textureness;
					deltaB *= textureness;
					
					Color c = sourcePixArray[i][j].getColor();
					int newR = c.getRed()+(int)deltaR;
					if(newR<0)
						newR = 0;
					if(newR>255)
						newR = 255;
					
					int newG = c.getGreen()+(int)deltaG;
					if(newG<0)
						newG = 0;
					if(newG>255)
						newG = 255;
					
					int newB = c.getBlue() + (int)deltaB;
					if(newB<0)
						newB = 0;
					if(newB>255)
						newB = 255;
					
					//System.out.println(" "+ deltaR + " " + deltaG + " " + deltaB);
					
					Color result = new Color(newR, newG, newB);
					biFiltered.setRGB(j, i, getIntFromColor(result));
				}
			}
			
			break;
		}

		case 3: /* bicubic upsampling + bilateral variation */ { 
			//biFiltered = ImageIO.read(new File(IMAGESOURCE));
			
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			int startingX = 0;
			int startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h* resizeScale; i++) {
				for (int j = 0; j < w* resizeScale; j++) {
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
							lines.clear();
						}
					}
					
					
					if(j<(regionWH/2))
						startingX = 0;
					else if (j>(w * resizeScale-1-(regionWH/2)))
						startingX = (int) (w * resizeScale -1-regionWH);
					else
						startingX = j - regionWH/2;
					
					if(i<(regionWH/2))
						startingY = 0;
					else if (i > h* resizeScale-1-(regionWH/2))
						startingY = (int) (h* resizeScale-1-regionWH);
					else 
						startingY = i - regionWH/2;
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[startingY+n][startingX+m].setWeight(calculateBilateralDistance(sourcePixArray[startingY+n][startingX+m],sourcePixArray[i][j]));
							pixStorage.add(sourcePixArray[startingY+n][startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
						if (jcb.isSelected())
							histogram[(int) temp.getIntensity()]++;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
						drawHistogram();
						
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			
			break;
		}
		
		case 4: /* bilateral variation */{
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			LinkedList<Pixel> pixStorage = new LinkedList<Pixel>();
			int startingX = 0;
			int startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w || mouseY >= h) {
							jtf.setText("mouse location exceed image boundaries");
							i = h;
							j = w;
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
						}
					}
					
					
					if(j<(regionWH/2))
						startingX = 0;
					else if (j>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = j - regionWH/2;
					
					if(i<(regionWH/2))
						startingY = 0;
					else if (i > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = i - regionWH/2;
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[startingY+n][startingX+m].setWeight(calculateBilateralDistance(sourcePixArray[startingY+n][startingX+m],sourcePixArray[i][j]));
							pixStorage.add(sourcePixArray[startingY+n][startingX+m]);
						}
					}
					
					Collections.sort(pixStorage);
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = h;
						j = w;
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			
			break;
		}
		case 5: /* Diff between Bilateral and Geodesic */{
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			
			BufferedImage bilateral = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			int startingX = 0;
			int startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					
					if(j<(regionWH/2))
						startingX = 0;
					else if (j>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = j - regionWH/2;
					
					if(i<(regionWH/2))
						startingY = 0;
					else if (i > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = i - regionWH/2;
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[startingY+n][startingX+m].setWeight(calculateBilateralDistance(sourcePixArray[startingY+n][startingX+m],sourcePixArray[i][j]));
							pixStorage.add(sourcePixArray[startingY+n][startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					bilateral.setRGB(j, i, getIntFromColor(tempColor));
					
					
					pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			System.out.println("bilaterla done");
			//==================================================================
			BufferedImage geodesic = ImageIO.read(new File(IMAGESOURCE));
			intPixArray = convertTo2DUsingGetRGB(bi);
			sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			//Pixel[][] resultPixArray = new Pixel[h][w];
			LinkedList<Pixel> pixStorage2 = new LinkedList<Pixel>();
			PriorityQueue<Pixel> maskPriorityQueue = new PriorityQueue<Pixel>();
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					// clean up storage & priority queue
					pixStorage2.clear();
					maskPriorityQueue.clear();

					/*
					 * //reset the weight of all of the pixels for(int m=0; m<h;
					 * m++) for(int n=0; n<w; n++)
					 * sourcePixArray[m][n].setWeight(999999999);
					 */

					// start populating the mask
					sourcePixArray[i][j].setWeight(0);
					maskPriorityQueue.add(sourcePixArray[i][j]);
					int pixStorageCount = 0;
					while (pixStorageCount < maskSize) {
						// get the lowest weighted pixel from the priority queue
						Pixel temp = maskPriorityQueue.remove();

						// find the four pixels adjacent to the selected pixel
						// the one on the left
						if (temp.getX() > 0) {
							Pixel left = sourcePixArray[temp.getY()][temp
									.getX() - 1];

							if (!left.isUsedByCurrentMask()) { // dr*dr +
																	// dg*dg +
																	// db*db
								double rangeDistance = intensityDiff(left.getColor(), sourcePixArray[i][j].getColor());
								double edgeCrossDistance = intensityDiff(left.getColor(), temp.getColor());
								double colorDistance = temp.getWeight() + rangeDistance + R* edgeCrossDistance;
								
								if (left.isUsedByCurrentPQ()) {
									if (colorDistance < left.getWeight())
										left.setWeight((int) colorDistance);
									else
										;
								} else {
									left.setWeight((int) colorDistance);
									left.setUsedByCurrentPQ(true);
									maskPriorityQueue.add(left);
								}
							}
						}

						// the one on the right
						if (temp.getX() < (w - 1)) {
							Pixel right = sourcePixArray[temp.getY()][temp
									.getX() + 1];
							if (!right.isUsedByCurrentMask()) { // dr*dr + dg*dg
																// + db*db
								double rangeDistance = intensityDiff(right.getColor(), sourcePixArray[i][j].getColor());
								double edgeCrossDistance = intensityDiff(right.getColor(), temp.getColor());
								double colorDistance = temp.getWeight() + rangeDistance + R* edgeCrossDistance;
								
								if (right.isUsedByCurrentPQ()) {
									if (colorDistance < right.getWeight())
										right.setWeight((int) colorDistance);
									else
										;
								} else {
									right.setWeight((int) colorDistance);
									right.setUsedByCurrentPQ(true);
									maskPriorityQueue.add(right);
								}
							}
						}
						// the one on top
						if (temp.getY() > 0) {
							Pixel top = sourcePixArray[temp.getY() - 1][temp
									.getX()];
							if (!top.isUsedByCurrentMask()) { // dr*dr + dg*dg +
																// db*db
								double rangeDistance = intensityDiff(top.getColor(), sourcePixArray[i][j].getColor());
								double edgeCrossDistance = intensityDiff(top.getColor(), temp.getColor());
								double colorDistance = temp.getWeight() + rangeDistance + R* edgeCrossDistance;
								
								if (top.isUsedByCurrentPQ()) {
									if (colorDistance < top.getWeight())
										top.setWeight((int) colorDistance);
									else
										;
								} else {
									top.setWeight((int) colorDistance);
									top.setUsedByCurrentPQ(true);
									maskPriorityQueue.add(top);
								}
							}
						}

						// the one on bottom
						if (temp.getY() < (h - 1)) {
							Pixel bottom = sourcePixArray[temp.getY() + 1][temp
									.getX()];
							if (!bottom.isUsedByCurrentMask()) { // dr*dr +
																	// dg*dg +
																	// db*db
								double rangeDistance = intensityDiff(bottom.getColor(), sourcePixArray[i][j].getColor());
								double edgeCrossDistance = intensityDiff(bottom.getColor(), temp.getColor());
								double colorDistance = temp.getWeight() + rangeDistance + R* edgeCrossDistance;
								
								
								if (bottom.isUsedByCurrentPQ()) {
									if (colorDistance < bottom.getWeight())
										bottom.setWeight((int) colorDistance);
									else
										;
								} else {
									bottom.setWeight((int) colorDistance);
									bottom.setUsedByCurrentPQ(true);
									maskPriorityQueue.add(bottom);
								}
							}
						}

						pixStorage2.add(temp);
						temp.setUsedByCurrentMask(true);
						pixStorageCount++;
					}

					// calculate the average color of the mask
					int sumR = 0, sumG = 0, sumB = 0;
					while (!pixStorage2.isEmpty()) {
						Pixel tempPix = pixStorage2.remove();
						tempPix.setWeight(999999999);
						tempPix.setUsedByCurrentMask(false);
						tempPix.setUsedByCurrentPQ(false);
						sumR += tempPix.getColor().getRed();
						sumG += tempPix.getColor().getGreen();
						sumB += tempPix.getColor().getBlue();
					}
					while (!maskPriorityQueue.isEmpty()) {
						Pixel temp = maskPriorityQueue.poll();
						temp.setUsedByCurrentMask(false);
						temp.setUsedByCurrentPQ(false);
					}
					sumR /= maskSize;
					sumG /= maskSize;
					sumB /= maskSize;

					Color tempColor = new Color(sumR, sumG, sumB);

					geodesic.setRGB(j, i, getIntFromColor(tempColor));
					

				}
				System.out.println("i" + i);
			}
			System.out.println("geodesic done");
			//========================================================
			
			int[][] intPixArray3 = convertTo2DUsingGetRGB(bilateral);
			Pixel[][] sourcePixArray3 = convert2DIntArrayToPixelArray(intPixArray3);
			int[][] intPixArray4 = convertTo2DUsingGetRGB(geodesic);
			Pixel[][] sourcePixArray4 = convert2DIntArrayToPixelArray(intPixArray4);
			int red = 0;
			int green = 0;
			int blue = 0;
			double absoluteDiff = 0;
			Color tempColor ;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					red = Math.abs(sourcePixArray3[i][j].getColor().getRed() - sourcePixArray4[i][j].getColor().getRed());
					green = Math.abs(sourcePixArray3[i][j].getColor().getGreen() - sourcePixArray4[i][j].getColor().getGreen());
					blue = Math.abs(sourcePixArray3[i][j].getColor().getBlue() - sourcePixArray4[i][j].getColor().getBlue());
					absoluteDiff = Math.sqrt(red*red + green*green + blue*blue);
					
					//-----color coding------
					if(absoluteDiff<5)
						tempColor = Color.GRAY;
					else if(absoluteDiff<10)
						tempColor = Color.BLUE;
					else if(absoluteDiff<20)
						tempColor = Color.CYAN;
					else if(absoluteDiff<30)
						tempColor = Color.GREEN;
					else if(absoluteDiff<40)
						tempColor = Color.YELLOW;
					else if(absoluteDiff<50)
						tempColor = Color.ORANGE;
					else
						tempColor = Color.red;
					
					//color coding
					

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
				}
				System.out.println("i" + i);
			}
			
			break;
			
		}
			

		case 6: /* upsampling + bilateral + fractional original*/{
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledPixArray = convert2DIntArrayToPixelArray(intPixArray2);
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			double startingX = 0;
			double startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
							lines.clear();
						}
					}
					
					double fractionJ = j/resizeScale;
					double fractionI = i/resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(fractionalCalculateBilateralDistance(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
						if (jcb.isSelected())
							histogram[(int) temp.getIntensity()]++;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);
					Pixel tempPixel  = new Pixel(1,1,tempColor,1);
					addLine(histogramStartX+(int)tempPixel.getIntensity(), histogramStartY, histogramStartX+(int)tempPixel.getIntensity(), histogramStartY+15, Color.green);
					
					
					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						
						biOrig = ImageIO.read(new File(IMAGESOURCE));
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biOrig.setRGB(temp.getX(), temp.getY(),
									MASKBACKGROUND); // red = 16711680
						}
						drawHistogram();
						addLine(histogramStartX+(int)upsampledPixArray[i][j].getIntensity(), histogramStartY, histogramStartX+(int)upsampledPixArray[i][j].getIntensity(), histogramStartY+15, Color.blue);
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i );
			}
			
			
			break;
		}
			
			
			
		case 7:/* upsampling + randomize texture + bilateral */{
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledPixArray = convert2DIntArrayToPixelArray(intPixArray2);
			
			
			//randomize texture
			Random rand = new Random();
			int r = 0;
			int redtemp = 0;
			int greentemp = 0;
			int bluetemp = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					Color cTemp = upsampledPixArray[i][j].getColor();
					r = rand.nextInt(randomRange*2+1) - randomRange;
					redtemp = cTemp.getRed();
					greentemp = cTemp.getGreen();
					bluetemp = cTemp.getBlue();
					redtemp += r;
					greentemp += r;
					bluetemp += r;
					if(redtemp<0)
						redtemp = 0;
					else if(redtemp>255)
						redtemp = 255;
					
					if(greentemp<0)
						greentemp = 0;
					else if(greentemp>255)
						greentemp = 255;
					
					if(bluetemp<0)
						bluetemp = 0;
					else if(bluetemp>255)
						bluetemp = 255;
					
					cTemp = new Color(redtemp, greentemp, bluetemp);
					upsampledPixArray[i][j].setColor(cTemp);
					//biFiltered.setRGB(j, i, getIntFromColor(cTemp));
				}
			}
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			double startingX = 0;
			double startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					double fractionJ = j/resizeScale;
					double fractionI = i/resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(fractionalCalculateBilateralDistance(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			break;
		}
		
		
		case 8:{  //===========  cross filter with original  ============
			
			bi = ImageIO.read(new File(ORIGINALIMAGE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			
			biFiltered = ImageIO.read(new File("textureAdded/multipleTextures_t0.2_newTexture.png"));
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledPixArray = convert2DIntArrayToPixelArray(intPixArray2);
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			double startingX = 0;
			double startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					double fractionJ = j/resizeScale;
					double fractionI = i/resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(fractionalCalculateBilateralDistance(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			
			break;
		}
		
		case 9: { // edge detection + collage
			//Texture t1 = new Texture("textures/cemete-texture.jpg");
			//Texture t2 = new Texture("textures/plate.jpg");
			//Texture t3 = new Texture("textures/grass.jpg");
			//Texture t4 = new Texture("textures/gravel.jpg");
			//Texture t5 = new Texture("textures/towel.jpg");
			
			Texture t1 = new Texture("textures/plate.jpg");
			Texture t2 = t1;
			Texture t3 = t1;
			Texture t4 = t1;
			Texture t5 = t1;
			
			
			biFiltered = ImageIO.read(new File(IMAGESOURCE)); //upsampled 2nd pass
			w = biFiltered.getWidth(null);
			h = biFiltered.getHeight(null);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			
			double tempIntensity = 0.0;
			double deltaR = 0.0;
			double deltaG = 0.0;
			double deltaB = 0.0;
			for(int i=0; i<h; i++){
				for(int j=0; j<w; j++){
					deltaR = 0.0;
					deltaG = 0.0;
					deltaB = 0.0;
					tempIntensity = sourcePixArray[i][j].getIntensity();
					if(tempIntensity<128){
						deltaR += t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].red * ((128-tempIntensity)/128);
						deltaG += t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].green * ((128-tempIntensity)/128);
						deltaB += t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].blue * ((128-tempIntensity)/128);
					}
					
					if(tempIntensity<192){
						deltaR += t2.getTexture()[i%t2.getHeight()][j%t2.getWidth()].red * ((128-Math.abs(64-tempIntensity))/128);
						deltaG += t2.getTexture()[i%t2.getHeight()][j%t2.getWidth()].green * ((128-Math.abs(64-tempIntensity))/128);
						deltaB += t2.getTexture()[i%t2.getHeight()][j%t2.getWidth()].blue * ((128-Math.abs(64-tempIntensity))/128);
					}
					
					if(tempIntensity<256){
						deltaR += t3.getTexture()[i%t3.getHeight()][j%t3.getWidth()].red * ((128-Math.abs(128-tempIntensity))/128);
						deltaG += t3.getTexture()[i%t3.getHeight()][j%t3.getWidth()].green * ((128-Math.abs(128-tempIntensity))/128);
						deltaB += t3.getTexture()[i%t3.getHeight()][j%t3.getWidth()].blue * ((128-Math.abs(128-tempIntensity))/128);
					}
					
					if(tempIntensity>=64){
						deltaR += t4.getTexture()[i%t4.getHeight()][j%t4.getWidth()].red * ((128-Math.abs(192-tempIntensity))/128);
						deltaG += t4.getTexture()[i%t4.getHeight()][j%t4.getWidth()].green * ((128-Math.abs(192-tempIntensity))/128);
						deltaB += t4.getTexture()[i%t4.getHeight()][j%t4.getWidth()].blue * ((128-Math.abs(192-tempIntensity))/128);
					}
					if(tempIntensity>=128){
						deltaR += t5.getTexture()[i%t5.getHeight()][j%t5.getWidth()].red * ((128-Math.abs(255-tempIntensity))/128);
						deltaG += t5.getTexture()[i%t5.getHeight()][j%t5.getWidth()].green * ((128-Math.abs(255-tempIntensity))/128);
						deltaB += t5.getTexture()[i%t5.getHeight()][j%t5.getWidth()].blue * ((128-Math.abs(255-tempIntensity))/128);
					}
					
					
					Color c = sourcePixArray[i][j].getColor();
					double textureness = 0.0;
					int neighbourPixCount = 0;
					Color temp = new Color(0);
					if(j>0){
						temp = sourcePixArray[i][j-1].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(j<(w-1)){
						temp = sourcePixArray[i][j+1].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(i>0){
						temp = sourcePixArray[i-1][j].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(i<(h-1)){
						temp = sourcePixArray[i+1][j].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					
					textureness/=neighbourPixCount;
					textureness/=196608; //(256^2)*3
					textureness*=texturenessStrength;
					//System.out.println(""+textureness);
					
					deltaR *= textureness;
					deltaG *= textureness;
					deltaB *= textureness;
					
					
					//fixed texture intensity
					//deltaR *= textureIntensity;
					//deltaG *= textureIntensity;
					//deltaB *= textureIntensity;
					
					c = sourcePixArray[i][j].getColor();
					int newR = c.getRed()+(int)deltaR;
					if(newR<0)
						newR = 0;
					if(newR>255)
						newR = 255;
					
					int newG = c.getGreen()+(int)deltaG;
					if(newG<0)
						newG = 0;
					if(newG>255)
						newG = 255;
					
					int newB = c.getBlue() + (int)deltaB;
					if(newB<0)
						newB = 0;
					if(newB>255)
						newB = 255;
					
					//System.out.println(" "+ deltaR + " " + deltaG + " " + deltaB);
					
					Color result = new Color(newR, newG, newB);
					biFiltered.setRGB(j, i, getIntFromColor(result));
				}
			}
			
			break;
		}
		
		case 10: { //textureness map
			biFiltered = ImageIO.read(new File(IMAGESOURCE)); //upsampled 2nd pass
			w = biFiltered.getWidth(null);
			h = biFiltered.getHeight(null);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			/*
			bi = ImageIO.read(new File(ORIGINALIMAGE));
			int[][] origIntPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] origSourcePixArray = convert2DIntArrayToPixelArray(origIntPixArray);
			int origW = bi.getWidth(null);
			int origH = bi.getHeight(null);
			*/
			
			double[][] texturenessMap = new double[h][w];
			avgTexturenessMap = new double[h][w];
			
			
			for(int i=0; i<h; i++){
				for(int j=0; j<w; j++){
					Color c = sourcePixArray[i][j].getColor();
					double textureness = 0.0;
					int neighbourPixCount = 0;
					Color temp = new Color(0);
					if(j>0){
						temp = sourcePixArray[i][j-1].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(j<(w-1)){
						temp = sourcePixArray[i][j+1].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(i>0){
						temp = sourcePixArray[i-1][j].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					if(i<(h-1)){
						temp = sourcePixArray[i+1][j].getColor();
						textureness+= Math.pow((c.getRed()-temp.getRed()), 2) + Math.pow((c.getGreen()-temp.getGreen()), 2) + Math.pow((c.getBlue()-temp.getBlue()), 2) ;
						neighbourPixCount++;
					}
					
					textureness/=neighbourPixCount;
					textureness/=196608; //(256^2)*3
					textureness*=256;
					textureness*=100;
					//System.out.println(""+textureness);
					texturenessMap[i][j] = textureness;
				}
			}
			for(int i=0; i<h; i++){
				for(int j=0; j<w; j++){
					int neighbourCount = 1; //the pixel itself counts as 1
					double avgTextureness = texturenessMap[i][j];
					if(i>0){
						avgTextureness+=texturenessMap[i-1][j];
						neighbourCount ++;
					}
					if(i<h-1){
						avgTextureness+=texturenessMap[i+1][j];
						neighbourCount ++;
					}
					if(j>0){
						avgTextureness+=texturenessMap[i][j-1];
						neighbourCount ++;
					}
					if(j<w-1){
						avgTextureness+=texturenessMap[i][j+1];
						neighbourCount ++;
					}
					avgTextureness/=neighbourCount;
					int temp = (int)avgTextureness;
					if(temp>255)
						temp = 255;
					
					avgTexturenessMap[i][j] = temp;
						
					Color result = new Color(temp, 0, (int)(255-temp));
					biFiltered.setRGB(j, i, getIntFromColor(result));
					
				}
			}
			
			
			break;
		}
		
		case 11: { // FAILED ATTEMPT: upsampling + fractional original + weight redistributed
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			int startingX = 0;
			int startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h* resizeScale; i++) {
				for (int j = 0; j < w* resizeScale; j++) {
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
						}
					}
					
					
					if(j<(regionWH/2))
						startingX = 0;
					else if (j>(w * resizeScale-1-(regionWH/2)))
						startingX = (int) (w * resizeScale -1-regionWH);
					else
						startingX = j - regionWH/2;
					
					if(i<(regionWH/2))
						startingY = 0;
					else if (i > h* resizeScale-1-(regionWH/2))
						startingY = (int) (h* resizeScale-1-regionWH);
					else 
						startingY = i - regionWH/2;
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[startingY+n][startingX+m].setWeight(calculateBilateralDistance(sourcePixArray[startingY+n][startingX+m],sourcePixArray[i][j]));
							pixStorage.add(sourcePixArray[startingY+n][startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					Random randgen = new Random();
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						double tempRand = randgen.nextDouble();
						if((1-k*(1/maskSize))>tempRand){
							Rsum += temp.getColor().getRed()/maskSize;
							Gsum += temp.getColor().getGreen()/maskSize;
							Bsum += temp.getColor().getBlue()/maskSize;
						}
						
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			
			break;
		}
		
		case 12: { // nearest neighbour upsampling + bilateral
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			int startingX = 0;
			int startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h* resizeScale; i++) {
				for (int j = 0; j < w* resizeScale; j++) {
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
						}
					}
					
					
					if(j<(regionWH/2))
						startingX = 0;
					else if (j>(w * resizeScale-1-(regionWH/2)))
						startingX = (int) (w * resizeScale -1-regionWH);
					else
						startingX = j - regionWH/2;
					
					if(i<(regionWH/2))
						startingY = 0;
					else if (i > h* resizeScale-1-(regionWH/2))
						startingY = (int) (h* resizeScale-1-regionWH);
					else 
						startingY = i - regionWH/2;
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[startingY+n][startingX+m].setWeight(calculateBilateralDistance(sourcePixArray[startingY+n][startingX+m],sourcePixArray[i][j]));
							pixStorage.add(sourcePixArray[startingY+n][startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			
			break;
		}
		
		case 13: { //upsampling + bilateral + lightlighter/darkdarker
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			int startingX = 0;
			int startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			double LLDDmodifier = 1.0;
			for (int i = 0; i < h* resizeScale; i++) {
				for (int j = 0; j < w* resizeScale; j++) {
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
						}
					}
					
					if(j<(regionWH/2))
						startingX = 0;
					else if (j>(w * resizeScale-1-(regionWH/2)))
						startingX = (int) (w * resizeScale -1-regionWH);
					else
						startingX = j - regionWH/2;
					
					if(i<(regionWH/2))
						startingY = 0;
					else if (i > h* resizeScale-1-(regionWH/2))
						startingY = (int) (h* resizeScale-1-regionWH);
					else 
						startingY = i - regionWH/2;
					
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							LLDDmodifier = 1.0;	
							if(sourcePixArray[i][j].getIntensity()>128){//lighterSide
								if(sourcePixArray[startingY+n][startingX+m].getIntensity()>=sourcePixArray[i][j].getIntensity())
									LLDDmodifier-=LLDDdelta;
								else
									LLDDmodifier+=LLDDdelta;
							}
							else{//darkerSide
								if(sourcePixArray[startingY+n][startingX+m].getIntensity()<=sourcePixArray[i][j].getIntensity())
									LLDDmodifier-=LLDDdelta;
								else
									LLDDmodifier+=LLDDdelta;
							}
								
							
							sourcePixArray[startingY+n][startingX+m].setWeight(LLDDmodifier*calculateBilateralDistance(sourcePixArray[startingY+n][startingX+m],sourcePixArray[i][j]));
							pixStorage.add(sourcePixArray[startingY+n][startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			
			
			break;
		}
		
		case 14: { //additive edge penalize 
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledPixArray = convert2DIntArrayToPixelArray(intPixArray2);
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			LinkedList<Pixel> maskStorage = new LinkedList<Pixel>();
			
			/*
			logKernel = new int[][]{
					{0,1,1,2,2,2,1,1,0},
					{1,2,4,5,5,5,4,2,1},
					{1,4,5,3,0,3,5,4,1},
					{2,5,3,-12,-24,-12,3,5,2},
					{2,5,0,-24,-40,-24,0,5,2},
					{2,5,3,-12,-24,-12,3,5,2},
					{1,4,5,3,0,3,5,4,1},
					{1,2,4,5,5,5,4,2,1},
					{0,1,1,2,2,2,1,1,0},
			};
			*/
			
			logKernel = new int[][]{
					{1, 1, 2, 1, 1},
					{1, -1, -2, -1, 1}, 
					{2, -2, -8, -2, 2}, 
					{1, -1, -2, -1, 1}, 
					{1, 1, 2, 1, 1}
			};
			
			if(MoG==null){
				MoG = new int[h][w];
				for (int i = 0; i<h; i++){
					for(int j=0; j<w; j++){
						
						double GradSum = 0;
						for(int ki = 0; ki<logKernel.length; ki++){
							for(int kj =0; kj<logKernel[0].length;kj++){
								if(i<2||i>(h-3)||j<2||j>(w-3)){
								}
								else
									GradSum+= sourcePixArray[i-2+ki][j-2+kj].getIntensity() * logKernel[ki][kj];
							}
						}
						MoG[i][j] = Tools.round(Math.abs((GradSum)));
						//System.out.println(MoG[i][j]);
					}
				}
			}
			
			double startingX = 0;
			double startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
							lines.clear();
						}
					}
					
					double fractionJ = j/resizeScale;
					double fractionI = i/resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(fractionalCalculateBilateralDistancePenalizeEdge(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						Rsum += temp.getColor().getRed()/maskSize;
						Gsum += temp.getColor().getGreen()/maskSize;
						Bsum += temp.getColor().getBlue()/maskSize;
					}
					Color tempColor = new Color((int)Rsum, (int)Gsum, (int)Bsum);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i);
			}
			break;
		}
		case 15: { //I'=I+d*S(d)
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledPixArray = convert2DIntArrayToPixelArray(intPixArray2);
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			LinkedList<Pixel> maskStorage = new LinkedList<Pixel>();
			
			/*
			logKernel = new int[][]{
					{0,1,1,2,2,2,1,1,0},
					{1,2,4,5,5,5,4,2,1},
					{1,4,5,3,0,3,5,4,1},
					{2,5,3,-12,-24,-12,3,5,2},
					{2,5,0,-24,-40,-24,0,5,2},
					{2,5,3,-12,-24,-12,3,5,2},
					{1,4,5,3,0,3,5,4,1},
					{1,2,4,5,5,5,4,2,1},
					{0,1,1,2,2,2,1,1,0},
			};
			*/
			
			logKernel = new int[][]{
					{1, 1, 2, 1, 1},
					{1, -1, -2, -1, 1}, 
					{2, -2, -8, -2, 2}, 
					{1, -1, -2, -1, 1}, 
					{1, 1, 2, 1, 1}
			};
			
			if(MoG==null){
				MoG = new int[h][w];
				for (int i = 0; i<h; i++){
					for(int j=0; j<w; j++){
						
						double GradSum = 0;
						for(int ki = 0; ki<logKernel.length; ki++){
							for(int kj =0; kj<logKernel[0].length;kj++){
								if(i<2||i>(h-3)||j<2||j>(w-3)){
								}
								else
									GradSum+= sourcePixArray[i-2+ki][j-2+kj].getIntensity() * logKernel[ki][kj];
							}
						}
						MoG[i][j] = Tools.round(Math.abs((GradSum)));
						//System.out.println(MoG[i][j]);
					}
				}
			}
			
			double startingX = 0;
			double startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
							lines.clear();
						}
					}
					
					double fractionJ = j/resizeScale;
					double fractionI = i/resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(fractionalCalculateBilateralDistancePenalizeEdge(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					histogram = new int[256];
					int[] preSmoothedHistogram = new int[256];
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						//Rsum += temp.getColor().getRed()/maskSize;
						//Gsum += temp.getColor().getGreen()/maskSize;
						//Bsum += temp.getColor().getBlue()/maskSize;
						preSmoothedHistogram[(int) temp.getIntensity()]++;
						scatterPlot[(int)temp.getLightness()][(int)temp.getHue()+180]++;
						avgMog+=MoG[temp.getY()][temp.getX()];
						maskStorage.add(temp);//store the mask data
					}
					avgMog/=maskSize;
					//smooth out the histogram
					histogram[0] = Tools.round((preSmoothedHistogram[0]*4 + preSmoothedHistogram[1]*2 + preSmoothedHistogram[2]*1)/7.0);
					histogram[1] = Tools.round((preSmoothedHistogram[0]*2 + preSmoothedHistogram[1]*4 + preSmoothedHistogram[2]*2 +preSmoothedHistogram[3]*1)/9.0);
					histogram[255] = Tools.round((preSmoothedHistogram[255]*4 + preSmoothedHistogram[254]*2 + preSmoothedHistogram[253]*1)/7.0);
					histogram[254] = Tools.round((preSmoothedHistogram[255]*2 + preSmoothedHistogram[254]*4 + preSmoothedHistogram[253]*2 +preSmoothedHistogram[252]*1)/9.0);
					for( int s=2; s<254; s++){
						histogram[s] = Tools.round((preSmoothedHistogram[s-2]*1+ preSmoothedHistogram[s-1]*2 + preSmoothedHistogram[s]*4 + preSmoothedHistogram[s+1]*2 +preSmoothedHistogram[s+2]*1)/10.0);
					}
					preSmoothedHistogram = new int[256];
					//find the mode of histogram
					int maxFreq = 0;
					int maxFreqIndex = 0; //aka, intensity of the mode of the mask 
					for( int s=0; s<256; s++){
						if(histogram[s]>=maxFreq){
							maxFreq = histogram[s];
							maxFreqIndex = s;
						}
					}
					double distance = maxFreqIndex - upsampledPixArray[i][j].getIntensity();
					double outputIntensity = upsampledPixArray[i][j].getIntensity() + distance* calculateS(distance);
					
					int realMaskCounter = 0;
					
					//use the mode
					while(!maskStorage.isEmpty()){
						Pixel temp = maskStorage.poll();
						//if((temp.getIntensity())<outputIntensity+5 && temp.getIntensity()>outputIntensity-4){
							Rsum += temp.getColor().getRed();
							Gsum += temp.getColor().getGreen();
							Bsum += temp.getColor().getBlue();
							realMaskCounter++;
						//}
					}
					
					Color tempColor = new Color((int)(Rsum/realMaskCounter), (int)(Gsum/realMaskCounter), (int)(Bsum/realMaskCounter));
					
					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						Pixel tempPixel  = new Pixel(1,1,tempColor,1);
						addLine(histogramStartX+(int)tempPixel.getIntensity()+1, histogramStartY, histogramStartX+(int)tempPixel.getIntensity()+1, histogramStartY+30, Color.green);
						addLine(histogramStartX+(int)maxFreqIndex, histogramStartY, histogramStartX+(int)maxFreqIndex, histogramStartY+15, Color.red);
						
						PriorityQueue<Pixel> leftOverQueue = new PriorityQueue<Pixel>();
						biOrig = ImageIO.read(new File(IMAGESOURCE));
						
						if(MoGisOn == true){
							for(int ii=0; ii<MoG.length; ii++){
								for(int jj=0; jj<MoG[0].length; jj++){
									int temp = MoG[ii][jj];
									if(temp>255)
										temp = 255;
									Color tempC = new Color(temp, temp, temp);
									biOrig.setRGB(jj, ii, getIntFromColor(tempC));
								}
							}
						}
						
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biOrig.setRGB(temp.getX(), temp.getY(),
									MASKBACKGROUND); // red = 16711680
							temp.setWeight((temp.getX()-j)*(temp.getX()-j)+ (temp.getY()-i)*(temp.getY()-i)); //euclidean
							leftOverQueue.add(temp);
						}
						drawHistogram();
						drawScatterPlot();
						
						histogram = new int[256];
						for(int p=0; p<50;p++){
							Pixel temp = leftOverQueue.poll();
							//System.out.print(temp.getIntensity());
							histogram[(int)temp.getIntensity()]++;
						}
						drawLeftOverHistogram();
						addLine(histogramStartX+(int)upsampledPixArray[i][j].getIntensity(), histogramStartY, histogramStartX+(int)upsampledPixArray[i][j].getIntensity(), histogramStartY+15, Color.blue);
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						//pixStorage.clear();
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i );
			}
			
			
			break;
		}
		case 16: { //contrast enhancement
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledPixArray = convert2DIntArrayToPixelArray(intPixArray2);
			
			PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
			LinkedList<Pixel> maskStorage = new LinkedList<Pixel>();
			double startingX = 0;
			double startingY = 0;
			double Rsum = 0;
			double Gsum = 0;
			double Bsum = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					
					//show local mask
					if (jcb.isSelected()) {
						if (mouseX >= w*resizeScale || mouseY >= h*resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale);
							j = (int) (w * resizeScale);
							continue;
						}
						else {
							
							j = mouseX;
							i = mouseY;
							lines.clear();
						}
					}
					
					double fractionJ = j/resizeScale;
					double fractionI = i/resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w-1-(regionWH/2)))
						startingX = w-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h-1-(regionWH/2))
						startingY = h-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							sourcePixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(fractionalCalculateBilateralDistance(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					Rsum = 0;
					Gsum = 0;
					Bsum = 0;
					histogram = new int[256];
					int[] preSmoothedHistogram = new int[256];
					for( int k = 0; k<maskSize; k++){
						Pixel temp = pixStorage.poll();
						//Rsum += temp.getColor().getRed()/maskSize;
						//Gsum += temp.getColor().getGreen()/maskSize;
						//Bsum += temp.getColor().getBlue()/maskSize;
						preSmoothedHistogram[(int) temp.getIntensity()]++;
						scatterPlot[(int)temp.getLightness()][(int)temp.getHue()+180]++;
						maskStorage.add(temp);//store the mask data
					}
					//smooth out the histogram
					histogram[0] = Tools.round((preSmoothedHistogram[0]*4 + preSmoothedHistogram[1]*2 + preSmoothedHistogram[2]*1)/7.0);
					histogram[1] = Tools.round((preSmoothedHistogram[0]*2 + preSmoothedHistogram[1]*4 + preSmoothedHistogram[2]*2 +preSmoothedHistogram[3]*1)/9.0);
					histogram[255] = Tools.round((preSmoothedHistogram[255]*4 + preSmoothedHistogram[254]*2 + preSmoothedHistogram[253]*1)/7.0);
					histogram[254] = Tools.round((preSmoothedHistogram[255]*2 + preSmoothedHistogram[254]*4 + preSmoothedHistogram[253]*2 +preSmoothedHistogram[252]*1)/9.0);
					for( int s=2; s<254; s++){
						histogram[s] = Tools.round((preSmoothedHistogram[s-2]*1+ preSmoothedHistogram[s-1]*2 + preSmoothedHistogram[s]*4 + preSmoothedHistogram[s+1]*2 +preSmoothedHistogram[s+2]*1)/10.0);
					}
					preSmoothedHistogram = new int[256];
					//find the mode of histogram
					int maxFreq = 0;
					int maxFreqIndex = 0; //aka, intensity of the mode of the mask 
					for( int s=0; s<256; s++){
						if(histogram[s]>=maxFreq){
							maxFreq = histogram[s];
							maxFreqIndex = s;
						}
					}
					double distance = maxFreqIndex - upsampledPixArray[i][j].getIntensity();
					double outputIntensity = upsampledPixArray[i][j].getIntensity() + distance* calculateS(distance);
					
					int realMaskCounter = 0;
					while(!maskStorage.isEmpty()){
						Pixel temp = maskStorage.poll();
						if((temp.getIntensity())<outputIntensity+5 && temp.getIntensity()>outputIntensity-4){
							Rsum += temp.getColor().getRed();
							Gsum += temp.getColor().getGreen();
							Bsum += temp.getColor().getBlue();
							realMaskCounter++;
						}
					}
					
					Color tempColor = new Color((int)(Rsum/realMaskCounter), (int)(Gsum/realMaskCounter), (int)(Bsum/realMaskCounter));
					
					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						Pixel tempPixel  = new Pixel(1,1,tempColor,1);
						addLine(histogramStartX+(int)tempPixel.getIntensity()+1, histogramStartY, histogramStartX+(int)tempPixel.getIntensity()+1, histogramStartY+30, Color.green);
						addLine(histogramStartX+(int)maxFreqIndex, histogramStartY, histogramStartX+(int)maxFreqIndex, histogramStartY+15, Color.red);
						
						PriorityQueue<Pixel> leftOverQueue = new PriorityQueue<Pixel>();
						biOrig = ImageIO.read(new File(IMAGESOURCE));
						
						while(!pixStorage.isEmpty()){
							Pixel temp = pixStorage.poll();
							biOrig.setRGB(temp.getX(), temp.getY(),
									MASKBACKGROUND); // red = 16711680
							temp.setWeight((temp.getX()-j)*(temp.getX()-j)+ (temp.getY()-i)*(temp.getY()-i)); //euclidean
							leftOverQueue.add(temp);
						}
						drawHistogram();
						drawScatterPlot();
						
						histogram = new int[256];
						for(int p=0; p<50;p++){
							Pixel temp = leftOverQueue.poll();
							//System.out.print(temp.getIntensity());
							histogram[(int)temp.getIntensity()]++;
						}
						drawLeftOverHistogram();
						addLine(histogramStartX+(int)upsampledPixArray[i][j].getIntensity(), histogramStartY, histogramStartX+(int)upsampledPixArray[i][j].getIntensity(), histogramStartY+15, Color.blue);
						i = (int) (h*resizeScale);
						j = (int) (w*resizeScale);
						//pixStorage.clear();
					}
					else
						pixStorage.clear();
				}
				System.out.println("i" + i );
			}
			
			break;
		}
		case 17: { //spring mass
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			
			//populate rx ry, original pixel's real position on upsampled image
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			/*
			logKernel = new int[][]{
					{0,1,1,2,2,2,1,1,0},
					{1,2,4,5,5,5,4,2,1},
					{1,4,5,3,0,3,5,4,1},
					{2,5,3,-12,-24,-12,3,5,2},
					{2,5,0,-24,-40,-24,0,5,2},
					{2,5,3,-12,-24,-12,3,5,2},
					{1,4,5,3,0,3,5,4,1},
					{1,2,4,5,5,5,4,2,1},
					{0,1,1,2,2,2,1,1,0},
			};
			*/
			
			logKernel = new int[][]{
					{1, 1, 2, 1, 1},
					{1, -1, -2, -1, 1}, 
					{2, -2, -8, -2, 2}, 
					{1, -1, -2, -1, 1}, 
					{1, 1, 2, 1, 1}
			};
			
			if(MoG==null){
				MoG = new int[h][w];
				for (int i = 0; i<h; i++){
					//System.out.println();
					//System.out.print(" "+ i + " ");
					for(int j=0; j<w; j++){
						double GradSum = 0;
						for(int ki = 0; ki<logKernel.length; ki++){
							for(int kj =0; kj<logKernel[0].length;kj++){
								if(i<2||i>(h-3)||j<2||j>(w-3)){
								}
								else
									GradSum+= sourcePixArray[i-2+ki][j-2+kj].getIntensity() * logKernel[ki][kj];
							}
						}
						MoG[i][j] = Tools.round(Math.abs((GradSum)));
						//use weight to represent the resting length of the springs
						double temp = resizeScale*100/MoG[i][j];
						if(temp>=resizeScale)
							temp = resizeScale;
						if(temp<=1)
							temp = 1;
						sourcePixArray[i][j].setWeight(temp); 
						//System.out.print(" "+ temp);
					}
				}
			}
			
			int timeStep = 0;
			
			while(timeStep<TIMESTEPS){
				//biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				timeStep++;
				Vec2 forceNorm;
				double moveDist;
				for(int i = 2; i<(h-2); i++){
					//System.out.println();
					//System.out.print(" "+ i + " ");
					for(int j=2; j<(w-2); j++){
						Vec2 forceSum = new Vec2(0,0);
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray[i][j], sourcePixArray[i+1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenPixelCurrentAndOriginal(sourcePixArray[i][j]));
						
						//System.out.print(" "+forceSum.x+" "+forceSum.y);
						forceNorm = Vec2.normalize(forceSum);
						moveDist = forceSum.getLength();
						if (moveDist>1)
							moveDist = 1.;
						//System.out.print(" "+moveDist);
						sourcePixArray[i][j].rx += forceNorm.x * moveDist;
						sourcePixArray[i][j].ry += forceNorm.y * moveDist;
						//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					}
				}
				System.out.println("Done time step: " + timeStep);
			}
			
			//bounding box
			double left, right, top, bottom;
			Vec2 E, F, G;
			Color A, B, C, D;
			int r,g,b;
			
			for(int i = 2; i<(h-2); i++){
				for(int j=2; j<(w-2); j++){
					if(!jcb.isSelected()){
						
						
						left = sourcePixArray[i][j].rx<sourcePixArray[i+1][j].rx?sourcePixArray[i][j].rx:sourcePixArray[i+1][j].rx;
						right = sourcePixArray[i][j+1].rx>sourcePixArray[i+1][j+1].rx?sourcePixArray[i][j+1].rx:sourcePixArray[i+1][j+1].rx;
						top = sourcePixArray[i][j].ry<sourcePixArray[i][j+1].ry?sourcePixArray[i][j].ry:sourcePixArray[i][j+1].ry;
						bottom = sourcePixArray[i+1][j].ry>sourcePixArray[i+1][j+1].ry?sourcePixArray[i+1][j].ry:sourcePixArray[i+1][j+1].ry;
						A = sourcePixArray[i][j].getColor();
						B = sourcePixArray[i][j+1].getColor();
						C = sourcePixArray[i+1][j+1].getColor();
						D = sourcePixArray[i+1][j].getColor();
						for(int m = (int)top; m<=(int)bottom; m++){
							for(int n=(int)left; n<=(int)right; n++){
								E = Vec2.Vec2Sub(sourcePixArray[i][j+1].getVec2(), sourcePixArray[i][j].getVec2());
								F = Vec2.Vec2Sub(sourcePixArray[i+1][j+1].getVec2(), sourcePixArray[i][j].getVec2());
								G = Vec2.Vec2Sub(Vec2.Vec2Add(Vec2.Vec2Sub(sourcePixArray[i][j].getVec2(), sourcePixArray[i+1][j].getVec2()), sourcePixArray[i+1][j+1].getVec2()), sourcePixArray[i+1][j].getVec2());
								Vec2 uv = invBilinear( new Vec2(n, m), sourcePixArray[i][j].getVec2(), sourcePixArray[i][j+1].getVec2(), sourcePixArray[i+1][j+1].getVec2(), sourcePixArray[i+1][j].getVec2());
								if(uv.x>=0){
									r =(int)(A.getRed() + (B.getRed()-A.getRed())*uv.x+ (D.getRed()-A.getRed())*uv.y + (A.getRed()-B.getRed()+C.getRed()-D.getRed())*uv.x*uv.y);
									g = (int)(A.getGreen() + (B.getGreen()-A.getGreen())*uv.x+ (D.getGreen()-A.getGreen())*uv.y + (A.getGreen()-B.getGreen()+C.getGreen()-D.getGreen())*uv.x*uv.y);
									b = (int)(A.getBlue() + (B.getBlue()-A.getBlue())*uv.x+ (D.getBlue()-A.getBlue())*uv.y + (A.getBlue()-B.getBlue()+C.getBlue()-D.getBlue())*uv.x*uv.y);
									Color temp = new Color(r,g,b);
									biFiltered.setRGB(n, m, getIntFromColor(temp));
								}
							}
						}
						biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					}
					else{
						//addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i+1][j].rx), Tools.round(sourcePixArray[i+1][j].ry),Color.RED);
						//addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i][j+1].rx), Tools.round(sourcePixArray[i][j+1].ry),Color.RED);
						//addPoint(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),sourcePixArray[i][j].getColor(),12);
					}
				}
			}
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 18:{//1D visulization of mass-spring system
			bi = ImageIO.read(new File(IMAGESOURCE));
			if(redoCalcFlag==true){
				int[][] intPixArray = convertTo2DUsingGetRGB(bi);
				sourcePixArrayFor1D = convert2DIntArrayToPixelArray(intPixArray);
			}
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			
			int Xorig = 0;
			int Yorig = 800;
			
			if (mouseX >= w || mouseY >= h) {
				jtf.setText("mouse location exceed image boundaries");
			}
			else{
				//calculate the mass-spring
				
				//populate rx ry, original pixel's real position on upsampled image
				
				if(redoCalcFlag==true){
					for(int j=0; j<w; j++){
						sourcePixArrayFor1D[mouseY][j].rx = j*resizeScale;
						sourcePixArrayFor1D[mouseY][j].ry = mouseY*resizeScale;
					}
					int timeStep = 0;
					while(timeStep<TIMESTEPS){
						//biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
						timeStep++;
						Vec2 forceNorm;
						double moveDist;
						for(int i = 1; i<w-1; i++){
							Vec2 forceSum = new Vec2(0,0);
							
							forceSum = Vec2.Vec2Add(forceSum, calcforce1D(sourcePixArrayFor1D, sourcePixArrayFor1D[mouseY][i],sourcePixArrayFor1D[mouseY][i-1]));
							forceSum = Vec2.Vec2Add(forceSum, calcforce1D(sourcePixArrayFor1D, sourcePixArrayFor1D[mouseY][i],sourcePixArrayFor1D[mouseY][i+1]));
							//forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenPixelCurrentAndOriginal(sourcePixArrayFor1D[mouseY][i]));
							
							//System.out.print(" "+forceSum.x+" "+forceSum.y);
							forceNorm = Vec2.normalize(forceSum);
							moveDist = forceSum.getLength();
							if (moveDist>1)
								moveDist = 1.;
							//System.out.print(" "+moveDist);
							sourcePixArrayFor1D[mouseY][i].rx += forceNorm.x * moveDist;
							//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
						}
						System.out.println("Done time step: " + timeStep);
					}
					redoCalcFlag = false;
				}
				
				
				//place the shifted
				//offset1D = 0;
				for(int i =0; i<w; i++){
					addPoint(Tools.round(sourcePixArrayFor1D[mouseY][i].rx)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Color.red, 4);
					if(i<w-1)
						addLine(Tools.round(sourcePixArrayFor1D[mouseY][i].rx)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Tools.round(sourcePixArrayFor1D[mouseY][i+1].rx)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i+1].getIntensity()),Color.red);
				}
				
				//place the ones from original grid
				if(showOriginalGrid){
					for(int i =0; i<w; i++){
						addPoint(Tools.round(i*resizeScale)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Color.black, 4);
						if(i<w-1)
							addLine(Tools.round(i*resizeScale)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Tools.round(i*resizeScale+resizeScale)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i+1].getIntensity()),Color.black);
					}
				}
			}
		}
		}
	}
	
	Vec2 calcforce1D(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		/*
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		double force = (displacement.getLength()-resizeScale*10/(10+intensityDiff(center.getColor(), out.getColor())))*springK;
		force -= force * damperC;
		return new Vec2(direction.x*force, direction.y*force);
		 */
		Vec2 displacement = new Vec2(out.rx-center.rx, 0);
		Vec2 direction = Vec2.normalize(displacement);
		double localWeight = 1/(10+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		double sumNeighborhoodWeight = 0.0;
		for(int i = Math.min(center.getX(), out.getX())-2; i<Math.min(center.getX(), out.getX())+3;i++){
			if(i>=0&&i<sourcePixArray[0].length-2){
				neighborhoodSpringCount++;
				sumNeighborhoodWeight+= 1/(10+intensityDiff(sourcePixArray[center.getY()][i].getColor(),sourcePixArray[center.getY()][i+1].getColor()));
			}
		}
		
		//double avgNeighborhoodDeltaI=sumNeighborhoodWeight/neighborhoodSpringCount;
		double localk = localWeight/sumNeighborhoodWeight;
		double force;
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			force = (displacement.getLength()-resizeScale)*springK/5;
		}
		else{
			force = (displacement.getLength()-resizeScale*localk*sumNeighborhoodWeight)*springK;
		}
		force -= force * damperC;
		return new Vec2(direction.x*force, 0);	
	}
	
	double cross(  Vec2 a,  Vec2 b ) { return a.x*b.y - a.y*b.x; }

	Vec2 invBilinear( Vec2 p, Vec2 a, Vec2 b, Vec2 c, Vec2 d )
	{
	    Vec2 e = Vec2.Vec2Sub(b,a);
	    Vec2 f = Vec2.Vec2Sub(d,a);
	    Vec2 g = Vec2.Vec2Sub(Vec2.Vec2Add(Vec2.Vec2Sub(a,b), c), d);
	    Vec2 h = Vec2.Vec2Sub(p,a);

	    double k2 = cross( g, f );
	    double k1 = cross( e, f ) + cross( h, g );
	    double k0 = cross( h, e );

	    double w = k1*k1 - 4.0*k0*k2;
	    if( w<0.0 ) return new Vec2(-1.0,0);
	    w = Math.sqrt( w );

	    double v1 = (-k1 - w)/(2.0*k2);
	    double u1 = (h.x - f.x*v1)/(e.x + g.x*v1);

	    double v2 = (-k1 + w)/(2.0*k2);
	    double u2 = (h.x - f.x*v2)/(e.x + g.x*v2);

	    double u = u1;
	    double v = v1;

	    if( v<0.0 || v>1.0 || u<0.0 || u>1.0 ) { u=u2;   v=v2;   }
	    if( v<0.0 || v>1.0 || u<0.0 || u>1.0 ) { u=-1.0; v=-1.0; }

	    return new Vec2( u, v );
	}
	
	private static Vec2 calcForceBetweenTwoPixel(Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		double force = (displacement.getLength()-resizeScale*10/(10+intensityDiff(center.getColor(), out.getColor())))*springK;
		force -= force * damperC;
		return new Vec2(direction.x*force, direction.y*force);
	}
	
	private static Vec2 calcForceBetweenPixelCurrentAndOriginal(Pixel p){
		Vec2 displacement = new Vec2(p.getX()*resizeScale - p.rx, p.getY()*resizeScale - p.ry);
		if(displacement.getLength()<=resizeScale*2){
			return new Vec2(0,0);
		}
		else{
			Vec2 direction = Vec2.normalize(displacement);
			double force = (displacement.getLength()-resizeScale*2)*springK;
			force -= force * damperC;
			return new Vec2(direction.x*force, direction.y*force);
		}
	}
	
	private static int calculateBilateralDistance (Pixel p1, Pixel p2){
		int Xdiff = p1.getX() - p2.getX();
		int Ydiff = p1.getY() - p2.getY();
		int intensityDiff = (int)(Math.pow((p1.getColor().getRed() - p2.getColor().getRed()),2) + 
						Math.pow((p1.getColor().getGreen() - p2.getColor().getGreen()),2)+
						Math.pow((p1.getColor().getBlue() - p2.getColor().getBlue()),2));
		double temp = Xdiff*Xdiff+Ydiff*Ydiff+ gamma * intensityDiff ;//+ avgTexturenessMap[p1.getY()][p1.getX()];
		
		return (int)Math.sqrt(temp);
	}
	
	private static double fractionalCalculateBilateralDistance (double x, double y, Color c, Pixel p2){
		double Xdiff = x - p2.getX();
		double Ydiff = y - p2.getY();
		int intensityDiff = (int)(Math.pow((c.getRed() - p2.getColor().getRed()),2) + 
						Math.pow((c.getGreen() - p2.getColor().getGreen()),2)+
						Math.pow((c.getBlue() - p2.getColor().getBlue()), 2));
		return Math.sqrt(Xdiff*Xdiff+Ydiff*Ydiff+ gamma * intensityDiff);// + avgTexturenessMap[(int)y][(int)x]*avgTexturenessMap[(int)y][(int)x]);
	}
	
	private static double fractionalCalculateBilateralDistancePenalizeEdge (double x, double y, Color c, Pixel p2){
		double Xdiff = x - p2.getX();
		double Ydiff = y - p2.getY();
		int intensityDiff = (int)(Math.pow((c.getRed() - p2.getColor().getRed()),2) + 
						Math.pow((c.getGreen() - p2.getColor().getGreen()),2)+
						Math.pow((c.getBlue() - p2.getColor().getBlue()), 2));
		return Math.sqrt(Xdiff*Xdiff+Ydiff*Ydiff+ gamma * intensityDiff) + alpha* MoG[p2.getY()][p2.getX()];// + avgTexturenessMap[(int)y][(int)x]*avgTexturenessMap[(int)y][(int)x]);
	}
	
	private static Pixel[][] convert2DIntArrayToPixelArray(int[][] intPixArray) {
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
	
	/*
	private static Color colorSub (Color p1, Color p2){
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
	*/
	
	private static Color colorPlus (Color p1, Color p2){
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

	private static int[][] convertTo2DUsingGetRGB(BufferedImage image) {
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
	public int getIntFromColor(Color c) {
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
	
	public  void addLine(int x1, int x2, int x3, int x4) {
	    addLine(x1, x2, x3, x4, Color.black);
	}

	public void addLine(int x1, int x2, int x3, int x4, Color color) {
	    lines.add(new Line(x1,x2,x3,x4, color));  
	    repaint();
	}
	
	public void addPoint(int x, int y, Color color, int r){
		points.add(new Point(x,y,color,r));
		repaint();
	}
	
	public double calculateS (double d){
		return 1/(1+Math.exp(-(modeM * (Math.abs(d)-modeK))));
	}

	public void actionPerformed(ActionEvent e) {
		JComboBox cb = (JComboBox) e.getSource();
		if (cb.getActionCommand().equals("SetFilter")) {
			setOpIndex(cb.getSelectedIndex());
			repaint();
		} else if (cb.getActionCommand().equals("Formats")) {
			/*
			 * Save the filtered image in the selected format. The selected item
			 * will be the name of the format to use
			 */
			String format = (String) cb.getSelectedItem();
			/*
			 * Use the format name to initialise the file suffix. Format names
			 * typically correspond to suffixes
			 */
			File saveFile = new File("savedimage." + format);
			JFileChooser chooser = new JFileChooser();
			chooser.setSelectedFile(saveFile);
			int rval = chooser.showSaveDialog(cb);
			if (rval == JFileChooser.APPROVE_OPTION) {
				saveFile = chooser.getSelectedFile();
				/*
				 * Write the filtered image in the selected format, to the file
				 * chosen by the user.
				 */
				try {
					ImageIO.write(biFiltered, format, saveFile);
				} catch (IOException ex) {
				}
			}
		}
	};
	
	public void drawHistogram(){
		
		for(int i=0; i<256;i++){
			if(histogram[i]!=0){
				addLine(histogramStartX+i, histogramStartY, histogramStartX+i, histogramStartY-(histogram[i]*3), histogramColor);
			}
		}
		histogram = new int[256];
	}
	
	public void drawScatterPlot(){
		for(int i=0; i<256;i++){
			for(int j=0; j<360; j++){
				if(scatterPlot[i][j]==0){
					//do nothing
				}
				else if(scatterPlot[i][j]<=1){
					addPoint(histogramStartX+i, histogramStartY+40+j, Color.blue,3);
				}
				else if(scatterPlot[i][j]<=3){
					addPoint(histogramStartX+i, histogramStartY+40+j, Color.ORANGE,3);
				}
				else
					addPoint(histogramStartX+i, histogramStartY+40+j, Color.red,3);
			}
		}
		scatterPlot = new int[256][360];
	}
	
	public void drawLeftOverHistogram(){
		for(int i=0; i<256;i++){
			if(histogram[i]!=0){
				addLine(histogramStartX+i, histogramStartY+50, histogramStartX+i, histogramStartY+50-(histogram[i]*3), histogramColor);
			}
		}
		histogram = new int[256];
	}

	public static void main(String s[]) {
		f = new JFrame("Geodesic Filter & Upsampling");
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		
		
		// p1 = new JPanel();
		final BilateralVariation si = new BilateralVariation();
		// p1.add(si);
		f.add("Center", si);
		JComboBox choices = new JComboBox(si.getDescriptions());
		choices.setActionCommand("SetFilter");
		choices.addActionListener(si);
		JComboBox formats = new JComboBox(si.getFormats());
		formats.setActionCommand("Formats");
		formats.addActionListener(si);
		JPanel panel = new JPanel();
		panel.add(choices);
		panel.add(new JLabel("Save As"));
		panel.add(formats);
		panel.add(jcb);
		// jtf.setSize(20, 10);
		panel.add(jtf);
		panel.add(load);
		panel.add(new JLabel("MaskSize"));
		panel.add(jtfMaskSize);
		panel.add(new JLabel("Scale"));
		panel.add(jtfScale);
		panel.add(new JLabel("Gamma"));
		panel.add(jtfGamma);
		panel.add(new JLabel("Alpha"));
		panel.add(jtfAlpha);
		panel.add(update);
		//si.drawHistogram();
		//si.addLine(500,60,500,500);
		//si.repaint();
		//si.repaint();
		//lines.clear();
		//panel.add(new JLabel("#PixInPot"));
		//panel.add(jtf_InpotPixCount);
		f.add("South", panel);
		f.pack();
		f.setVisible(true);
		f.setExtendedState(f.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		
		load.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				String temp = IMAGESOURCE;
				IMAGESOURCE = jtf.getText();
				try {
					bi = ImageIO.read(new File(IMAGESOURCE));
					biFiltered = bi;
					lastOp = opIndex+1;
					si.repaint();
				} catch (IOException e) {
					jtf.setText("Image not exist");
				}
			}
		});
		
		update.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				maskSize = Double.parseDouble(jtfMaskSize.getText());
				regionWH = (int) Math.sqrt(maskSize*16);
				resizeScale = Double.parseDouble(jtfScale.getText());
				int maxScale = 4000/bi.getWidth()>4000/bi.getHeight()?4000/bi.getWidth():4000/bi.getHeight();
				resizeScale = resizeScale>maxScale? maxScale:resizeScale;
				gamma = Double.parseDouble(jtfGamma.getText());
				alpha = Double.parseDouble(jtfAlpha.getText());
			}
		});

		f.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				mouseX = e.getX() - 8;
				mouseY = e.getY() - 30;
				jtf.setText("" + mouseX + ", " + mouseY);
				MoG=null;
				points.clear();
				lines.clear();
				redoCalcFlag = true;
				si.repaint();
				
			}
		});
		
		panel.setFocusable(true);
		panel.addKeyListener(new KeyAdapter() {
	         public void keyPressed(KeyEvent e) {                
	            if(e.getKeyCode() == KeyEvent.VK_W){
	               mouseY--;
	            }
	            else if(e.getKeyCode() == KeyEvent.VK_S){
		           mouseY++;
		        }
	            else if(e.getKeyCode() == KeyEvent.VK_A){
			           mouseX--;
			    }
	            else if(e.getKeyCode() == KeyEvent.VK_D){
			           mouseX++;
			    }
	            else if(e.getKeyCode() == KeyEvent.VK_M){
	            	if(MoGisOn == false)
	            		MoGisOn = true;
	            	else
	            		MoGisOn = false;
	            }
	            else if(e.getKeyCode() == KeyEvent.VK_C){
	            	if(circleIsOn == false)
	            		circleIsOn = true;
	            	else
	            		circleIsOn = false;
	            }
	            else if(e.getKeyCode() == KeyEvent.VK_O){
	            	if(showOriginalGrid == false)
	            		showOriginalGrid = true;
	            	else
	            		showOriginalGrid = false;
	            	points.clear();
	            	lines.clear();
	            }
	            else if(e.getKeyCode() == KeyEvent.VK_I){
	            	offset1D-=50;
	            	//redoCalcFlag = true;
	            	points.clear();
	            	lines.clear();
	            }
	            else if(e.getKeyCode() == KeyEvent.VK_P){
	            	offset1D+=50;
	            	//redoCalcFlag = true;
	            	points.clear();
	            	lines.clear();
	            }
	            jtf.setText("" + mouseX + ", " + mouseY);
	            MoG=null;
	            points.clear();
				si.repaint();
	         }        
	      });

	}
}