

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

public class Voronoi extends Component implements ActionListener {
	
	String descs[] = { "Original", "bicubic upsampled", "Voronoi Unified Weight", "dilation", "erosion","opening","closing","closing then opening","showMog"};

	//static String IMAGESOURCE = "texim/dragonfly.png";
	static String IMAGESOURCE = "testImg150324/1.png";
	//static String IMAGESOURCE = "testImg141219/all/9.jpg";
	//static String IMAGESOURCE = "dot2.png";
	//static String IMAGESOURCE = "lines_thick.jpg";
	static String ORIGINALIMAGE = "redpicnictable.jpg"; //used for cross-filtering with original image

	//final int maskSize = 200;
	
	static double maskSize = 100;
	static int regionWH = (int) Math.sqrt(maskSize*16); //width or height of the sample region
	//final int upsampleMaskSize = 10;
	final double R = 1; //for geodesic
	static double gamma = 0.1; // for bilateral, for weight on intensity diff
	static double resizeScale = 5;
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
	static double MoG[][];
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
	final double texturenessStrength = 0.004; 
	final double LLDDdelta = 0.99;
	private static double avgTexturenessMap[][];
	
	//for mass-spring
	final static double springK = 1;
	final static double damperC = 0.5;
	final static double angularSpringK = 1;
	
	static boolean circleIsOn = false;
	static boolean showOriginalGrid = true;
	static boolean redoCalcFlag = false;
	static int offset1D = 0;
	static Pixel[][] sourcePixArrayFor1D;
	static double smoothingK = 1;
	static double GradientK = 0.1;
	
	
	//--------------------------
	final int TIMESTEPS = 10;
	//-------------------------
	final double voronoiK = 0.05;
	//--------------------------

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

	public Voronoi() {
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
			/*
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
				g.drawLine(mouseX+10, mouseY, mouseX+50, mouseY);
				g.drawLine(mouseX-10, mouseY, 0, mouseY);
				g.drawLine(mouseX, mouseY-10, mouseX, mouseY-50);
				g.drawLine(mouseX, mouseY+10, mouseX, mouseY+50);
				g.setColor(Color.black);
			}
			g.setColor(Color.black);
			g.drawLine(histogramStartX, histogramStartY+400, histogramStartX, histogramStartY+40);
			g.drawLine(histogramStartX, histogramStartY+220, histogramStartX+300, histogramStartY+220);
			g.drawString("HUE", histogramStartX, histogramStartY+395);
			g.drawString("LIGHTNESS", histogramStartX+210, histogramStartY+235);
			g.drawString("AvgMoG: "+avgMog + " ", histogramStartX, histogramStartY+415);
			*/
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
			
		
		case 2: { 
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			preCalCGradient(sourcePixArray);
			
			int timeStepCounter = 0;
			final double MAXDIST = w*h*resizeScale*resizeScale;
			double shortestDist = MAXDIST;
			double tempDist = MAXDIST;
			Pixel closestPixel = sourcePixArray[0][0];
			int centerX=0;
			int centerY=0;
			while(timeStepCounter<=TIMESTEPS){
				System.out.println(" t" + timeStepCounter);
				timeStepCounter++;
				for(int i=0;i<h*resizeScale;i++){
					for(int j=0; j<w*resizeScale;j++){
						shortestDist = MAXDIST;
						centerX = (int) ((j+1)/resizeScale);
						centerY = (int) ((i+1)/resizeScale);
						for(int m=centerY-3; m<(centerY+4);m++){
							for(int n = centerX-3;n<(centerX+4); n++){
								if(m>=0&&n>=0&&m<h&&n<w){
									tempDist = voronoiDist(j,i,sourcePixArray[m][n]);
									if(tempDist<shortestDist){
										shortestDist = tempDist;
										closestPixel = sourcePixArray[m][n];
									}
								}
							}
						}
						closestPixel.xCumul+=j;
						closestPixel.yCumul+=i;
						closestPixel.cumulCount++;
					}
				}
				
				for(int i=0;i<sourcePixArray.length;i++){
					for(int j=0; j<sourcePixArray[0].length;j++){
						sourcePixArray[i][j].rx = 1.0*sourcePixArray[i][j].xCumul / sourcePixArray[i][j].cumulCount;
						sourcePixArray[i][j].ry = 1.0*sourcePixArray[i][j].yCumul / sourcePixArray[i][j].cumulCount;
						sourcePixArray[i][j].xCumul = 0;
						sourcePixArray[i][j].yCumul = 0;
						sourcePixArray[i][j].cumulCount = 0;
					}
				}
			}
			
			triangleBarycentric(sourcePixArray);
			
			/*for(int i=0;i<h*resizeScale;i++){
				for(int j=0; j<w*resizeScale;j++){
					shortestDist = MAXDIST;
					centerX = (int) (j/resizeScale);
					centerY = (int) (i/resizeScale);
					for(int m=centerY-3; m<centerY+4;m++){
						for(int n = centerX-3;n<centerX+4; n++){
							if(m>0&&n>0&&m<h&&n<w){
								tempDist = voronoiDist(j,i,sourcePixArray[m][n]);
								if(tempDist<shortestDist){
									shortestDist = tempDist;
									closestPixel = sourcePixArray[m][n];
								}
							}
						}
					}
					biFiltered.setRGB(j, i, getIntFromColor(closestPixel.getColor()));
				}
			}*/
			//triangleBarycentric(sourcePixArray);
			System.out.println("Done Printing");
			break;
		}
		case 3:{//dilation
			bi = ImageIO.read(new File(IMAGESOURCE));
			biFiltered = bi;
			dilation(biFiltered);
			break;
		}
		
		case 4:{//erosion
			bi = ImageIO.read(new File(IMAGESOURCE));
			biFiltered = bi;
			erosion(biFiltered);
			break;
		}
		case 5:{//opening
			bi = ImageIO.read(new File(IMAGESOURCE));
			biFiltered = bi;
			opening(biFiltered);
			break;
		}
		
		case 6:{//closing
			bi = ImageIO.read(new File(IMAGESOURCE));
			biFiltered = bi;
			closing(biFiltered);
			break;
		}
		case 7:{//closing then opening
			bi = ImageIO.read(new File(IMAGESOURCE));
			biFiltered = bi;
			closing(biFiltered);
			opening(biFiltered);
			break;
		}
		case 8:{//show mog
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = bi;
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			double maxG = preCalCGradient(sourcePixArray);
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					int c = (int)(255.0*(sourcePixArray[i][j].mog/maxG));
					Color col = new Color(c,c,c);
					bi.setRGB(j, i, getIntFromColor(col));
				}
			}
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			//re-center
			int timeStepCounter = 0;
			final double MAXDIST = w*h*resizeScale*resizeScale;
			double shortestDist = MAXDIST;
			double tempDist = MAXDIST;
			Pixel closestPixel = sourcePixArray[0][0];
			int centerX=0;
			int centerY=0;
			while(timeStepCounter<=TIMESTEPS){
				System.out.println(" t" + timeStepCounter);
				timeStepCounter++;
				for(int i=0;i<h*resizeScale;i++){
					for(int j=0; j<w*resizeScale;j++){
						shortestDist = MAXDIST;
						centerX = (int) ((j+1)/resizeScale);
						centerY = (int) ((i+1)/resizeScale);
						for(int m=centerY-3; m<(centerY+4);m++){
							for(int n = centerX-3;n<(centerX+4); n++){
								if(m>=0&&n>=0&&m<h&&n<w){
									tempDist = voronoiDist(j,i,sourcePixArray[m][n]);
									if(tempDist<shortestDist){
										shortestDist = tempDist;
										closestPixel = sourcePixArray[m][n];
									}
								}
							}
						}
						closestPixel.xCumul+=j;
						closestPixel.yCumul+=i;
						closestPixel.cumulCount++;
					}
				}
				
				for(int i=0;i<sourcePixArray.length;i++){
					for(int j=0; j<sourcePixArray[0].length;j++){
						sourcePixArray[i][j].rx = 1.0*sourcePixArray[i][j].xCumul / sourcePixArray[i][j].cumulCount;
						sourcePixArray[i][j].ry = 1.0*sourcePixArray[i][j].yCumul / sourcePixArray[i][j].cumulCount;
						sourcePixArray[i][j].xCumul = 0;
						sourcePixArray[i][j].yCumul = 0;
						sourcePixArray[i][j].cumulCount = 0;
						//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					}
				}
			}
			for(int i=0;i<sourcePixArray.length;i++){
				for(int j=0; j<sourcePixArray[0].length;j++){
					biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			break;
		}
		}
		
	}
	
	private double preCalCGradient(Pixel[][] pixArray) {
		for(int i=0; i<pixArray.length;i++){
			for(int j=0; j<pixArray[0].length;j++){
				pixArray[i][j].intensity = pixArray[i][j].getIntensity();
				//System.out.println("intensity "+pixArray[i][j].intensity);
			}
		}
		
		int VE[][] = {  //vertical
				{1,2,1},
				{0,0,0},
				{-1,-2,-1},
					
			};
		int HO[][] = {  //Horizontal
				{-1,0,1},
				{-2,0,2},
				{-1,0,1},
					
			};
		double maxG = 0;
		for(int i=0; i<pixArray.length;i++){
			for(int j=0; j<pixArray[0].length;j++){
				Vec2 gradient = new Vec2(0,0);
				if(i<1||j<1||i>=pixArray.length-1||j>=pixArray[0].length-1)
					gradient = new Vec2(0.00001,0.00001);
				else{
					int vert = 0;
					int hori = 0;
					for(int ki=0;ki<3;ki++){
						for(int kj=0;kj<3;kj++){
							vert+= pixArray[i+ki-1][j+kj-1].intensity*VE[ki][kj];
							hori+= pixArray[i+ki-1][j+kj-1].intensity*HO[ki][kj];
						}
					}
					gradient = new Vec2(hori,vert);
				}
				
				/*if(gradient.x<0.001&&gradient.y<0.001){
					gradient.x = 0.00001;
					gradient.y = 0.00001;
				}*/
				
				pixArray[i][j].mog = gradient.getLength()/4.0;
				pixArray[i][j].gradient = Vec2.normalize(gradient);
				//System.out.print(" x "+gradient.x+" y "+gradient.y);
				if(gradient.getLength()/4.0>maxG)
					maxG = gradient.getLength()/4.0;
					
			}
			//System.out.println();
		}
		return maxG;
		
	}

	private double voronoiDist(int x, int y, Pixel p){
		Vec2 displacement = new Vec2 (x-p.rx, y-p.ry);
		/*
		displacement.x *= (1+p.gradient.getLength()/80.0);
		displacement.y *= (1+p.gradient.getLength()/80.0);
		return (displacement.x*displacement.x)+(displacement.y*displacement.y);
		*/
		
		double xProjection = Vec2.dot(displacement, p.gradient);
		double yProjection = Vec2.dot(displacement, new Vec2(p.gradient.y, -p.gradient.x));
		xProjection *= (1+voronoiK*p.mog);
		double result = (xProjection*xProjection) + (yProjection*yProjection);
		result= result<0.0001?0.0001:result;
		return result;
		
	}
	
	private void dilation (BufferedImage bi){
		int w = bi.getWidth();
		int h = bi.getHeight();
		Color[][] colArray = convertColorTo2DUsingGetRGB(bi);
		double [][] lumArray = new double[h][w];
		for(int i=0; i<h; i++){
			for(int j=0; j<w; j++){
				lumArray[i][j] = calcLuminance(colArray[i][j]);
			}
		}
		/*
		//5x5
		int SE[][] = {  //structuring element
			{0,1,1,1,0},
			{1,1,1,1,1},
			{1,1,1,1,1},
			{1,1,1,1,1},
			{0,1,1,1,0},
				
		};
		*/
		/*
		//3x3
		int SE[][] = {  //structuring element
				{0,1,0},
				{1,1,1},
				{0,1,0},	
		};	
		*/
		/*
		int SE[][] = {  //structuring element
				{20,40,20},
				{40,50,40},
				{20,40,20},	
		};	
		*/
		/*
		int SE[][] = {  //structuring element
				{1,4,7,4,1},
				{4,16,26,16,4},
				{7,26,41,26,7},
				{4,16,26,16,4},
				{1,4,7,4,1},
		};	
		*/
		
		int SE[][] = {  //structuring element
				{1,2,1},
				{2,4,2},
				{1,2,1},	
		};	
		
		int delta = SE.length/2;
		for (int i = 0; i<h; i++){
			for(int j=0; j<w; j++){
				double max = lumArray[i][j]*SE[delta][delta];
				for(int ki = 0; ki<SE.length; ki++){
					for(int kj =0; kj<SE[0].length;kj++){
						if(i+ki-delta<0||i+ki-delta>h-1||j+kj-delta<0||j+kj-delta>w-1){
							continue;
						}
						else{
							if(lumArray[i+ki-delta][j+kj-delta]*SE[ki][kj]>max){
								max = lumArray[i+ki-delta][j+kj-delta]*SE[ki][kj];
								bi.setRGB(j, i, bi.getRGB(j+kj-delta, i+ki-delta));
							}
						}
						
					}
				}
			}
		}
	}
	
	
	
	private double calcLuminance(Color c){
		return 0.30*c.getRed()+0.59*c.getGreen()+0.11*c.getBlue();
	}
	
	private void erosion (BufferedImage bi){
		int w = bi.getWidth();
		int h = bi.getHeight();
		Color[][] colArray = convertColorTo2DUsingGetRGB(bi);
		double [][] lumArray = new double[h][w];
		for(int i=0; i<h; i++){
			for(int j=0; j<w; j++){
				lumArray[i][j] = calcLuminance(colArray[i][j]);
			}
		}
		/*
		//5x5
		int SE[][] = {  //structuring element
			{-1,1,1,1,-1},
			{1,1,1,1,1},
			{1,1,1,1,1},
			{1,1,1,1,1},
			{-1,1,1,1,-1},	
		};
		*/
		
		/*
		//3x3
		int SE[][] = {  //structuring element
				{-1,1,-1},
				{1,1,1},
				{-1,1,-1},	
		};
		*/
		/*
		int SE[][] = {  //structuring element
				{50,40,50},
				{40,20,40},
				{50,40,50},	
		};	
		*/
		/*
		int SE[][] = {  //structuring element
				{1000/1,1000/4,1000/7,1000/4,1000/1},
				{1000/4,1000/16,1000/26,1000/16,1000/4},
				{1000/7,1000/26,1000/41,1000/26,1000/7},
				{1000/4,1000/16,1000/26,1000/16,1000/4},
				{1000/1,1000/4,1000/7,1000/4,1000/1},
		};
		*/
		int SE[][] = {  //structuring element
				{4,2,4},
				{2,1,2},
				{4,2,4},	
		};	
		
		int delta = SE.length/2;
		
		for (int i = 0; i<h; i++){
			for(int j=0; j<w; j++){
				double min = lumArray[i][j]*SE[delta][delta];
				for(int ki = 0; ki<SE.length; ki++){
					for(int kj =0; kj<SE[0].length;kj++){
						if(i+ki-delta<0||i+ki-delta>h-1||j+kj-delta<0||j+kj-delta>w-1){
							continue;
						}
						else{
							if(SE[ki][kj]!=-1&&lumArray[i+ki-delta][j+kj-delta]*SE[ki][kj]<min){
								min = lumArray[i+ki-delta][j+kj-delta]*SE[ki][kj];
								bi.setRGB(j, i, bi.getRGB(j+kj-delta, i+ki-delta));
							}
						}
						
					}
				}
			}
		}
	}
	
	private void opening(BufferedImage bi){
		erosion(bi);
		dilation(bi);
	}
	private void closing(BufferedImage bi){
		dilation(bi);
		erosion(bi);
	}
	
	private void inverseBilinear( Pixel[][] sourcePixArray){
		double left, right, top, bottom;
		Vec2 E, F, G;
		Color A, B, C, D;
		int r,g,b;
		
		for(int i = 2; i<sourcePixArray.length-1; i++){
			for(int j=2; j<sourcePixArray[0].length -1; j++){
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
							Random rand = new Random();
							if(uv.x>=0){
								
								r =(int)(A.getRed() + (B.getRed()-A.getRed())*uv.x+ (D.getRed()-A.getRed())*uv.y + (A.getRed()-B.getRed()+C.getRed()-D.getRed())*uv.x*uv.y);
								g = (int)(A.getGreen() + (B.getGreen()-A.getGreen())*uv.x+ (D.getGreen()-A.getGreen())*uv.y + (A.getGreen()-B.getGreen()+C.getGreen()-D.getGreen())*uv.x*uv.y);
								b = (int)(A.getBlue() + (B.getBlue()-A.getBlue())*uv.x+ (D.getBlue()-A.getBlue())*uv.y + (A.getBlue()-B.getBlue()+C.getBlue()-D.getBlue())*uv.x*uv.y);
								
								//r = rand.nextInt(255);
								//g = rand.nextInt(255);
								//b = rand.nextInt(255);
								Color temp = new Color(r,g,b);
								biFiltered.setRGB(n, m, getIntFromColor(temp));
							}
						}
					}
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
				else{
					addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i+1][j].rx), Tools.round(sourcePixArray[i+1][j].ry),Color.blue);
					addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i][j+1].rx), Tools.round(sourcePixArray[i][j+1].ry),Color.RED);
					//addPoint(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),sourcePixArray[i][j].getColor(),12);
				}
			}
		}
	}
	
	private void triangleBarycentric(Pixel[][] sourcePixArray){
		
		double left, right, top, bottom; //for bounding boxes
		Pixel t1a, t1b, t1c, t2a, t2b, t2c;
		Color A, B, C, D;
		int r,g,b;
		double dist1, dist2;
		
		for(int i = 2; i<sourcePixArray.length-1; i++){
			for(int j=2; j<sourcePixArray[0].length -1; j++){
				if(!jcb.isSelected()){
					
					//figure out how to divide quads
					dist1 = intensityDiff(sourcePixArray[i+1][j+1].getColor(),sourcePixArray[i][j].getColor()); //Math.pow((sourcePixArray[i+1][j+1].rx - sourcePixArray[i][j].rx),2) + Math.pow((sourcePixArray[i+1][j+1].ry - sourcePixArray[i][j].ry),2);
					dist2 = intensityDiff(sourcePixArray[i][j+1].getColor(),sourcePixArray[i+1][j].getColor()); //Math.pow((sourcePixArray[i][j+1].rx - sourcePixArray[i+1][j].rx),2) + Math.pow((sourcePixArray[i][j+1].ry - sourcePixArray[i+1][j].ry),2);
					t1a = sourcePixArray[i][j];
					t1c = sourcePixArray[i+1][j];
					t2b = sourcePixArray[i][j+1];
					t2c = sourcePixArray[i+1][j+1];
					if(dist1>dist2){
						t1b = sourcePixArray[i][j+1];
						t2a = sourcePixArray[i+1][j];
					}
					else{
						t1b = sourcePixArray[i+1][j+1];
						t2a = sourcePixArray[i][j];
					}
					
					left = sourcePixArray[i][j].rx<sourcePixArray[i+1][j].rx?sourcePixArray[i][j].rx:sourcePixArray[i+1][j].rx;
					right = sourcePixArray[i][j+1].rx>sourcePixArray[i+1][j+1].rx?sourcePixArray[i][j+1].rx:sourcePixArray[i+1][j+1].rx;
					top = sourcePixArray[i][j].ry<sourcePixArray[i][j+1].ry?sourcePixArray[i][j].ry:sourcePixArray[i][j+1].ry;
					bottom = sourcePixArray[i+1][j].ry>sourcePixArray[i+1][j+1].ry?sourcePixArray[i+1][j].ry:sourcePixArray[i+1][j+1].ry;
					
					for(int m = (int)top; m<=(int)bottom; m++){
						for(int n=(int)left; n<=(int)right; n++){
							Pixel temp = new Pixel();
							temp.rx = n;
							temp.ry = m;
							//System.out.println(" "+temp.rx+" "+temp.ry);
							//System.out.println(" "+"("+t1a.rx+","+t1a.ry+")"+"("+t1b.rx+","+t1b.ry+")"+"("+t1c.rx+","+t1c.ry+")"+"("+temp.rx+","+temp.ry+")"+(turns(t1a, t1b, temp)<=0)+" "+(turns(t1b,t1c,temp)<=0)+" "+(turns(t1c,t1a,temp)<=0));
							if(turns(t1a, t1b, temp)<=0&&turns(t1b,t1c,temp)<=0&&turns(t1c,t1a,temp)<=0){ //lies inside triangle1
								double combinedTriangleArea = triArea(t1a, t1b, t1c);
								r = (int)( t1a.getColor().getRed()*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.getColor().getRed()*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.getColor().getRed()*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								g = (int)( t1a.getColor().getGreen()*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.getColor().getGreen()*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.getColor().getGreen()*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								b = (int)( t1a.getColor().getBlue()*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.getColor().getBlue()*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.getColor().getBlue()*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								r=r>255?255:r;
								g=g>255?255:g;
								b=b>255?255:b;
								Color tempC = new Color(r,g,b);
								biFiltered.setRGB(n, m, getIntFromColor(tempC));
							}
							else if(turns(t2a, t2b, temp)<=0&&turns(t2b,t2c,temp)<=0&&turns(t2c,t2a,temp)<=0){ //lies inside triangle2
								double combinedTriangleArea = triArea(t2a, t2b, t2c);
								r = (int)( t2a.getColor().getRed()*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.getColor().getRed()*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.getColor().getRed()*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								g = (int)( t2a.getColor().getGreen()*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.getColor().getGreen()*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.getColor().getGreen()*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								b = (int)( t2a.getColor().getBlue()*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.getColor().getBlue()*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.getColor().getBlue()*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								r=r>255?255:r;
								g=g>255?255:g;
								b=b>255?255:b;
								
								Color tempC = new Color(r,g,b);
								biFiltered.setRGB(n, m, getIntFromColor(tempC));
							}
							else{
								//biFiltered.setRGB(n, m, getIntFromColor(Color.black));
							}
						}
					}
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
				else{
					addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i+1][j].rx), Tools.round(sourcePixArray[i+1][j].ry),Color.blue);
					addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i][j+1].rx), Tools.round(sourcePixArray[i][j+1].ry),Color.RED);
					//addPoint(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),sourcePixArray[i][j].getColor(),12);
				}
			}
		}
	}
	
	private void triangleBarycentricTexture(Pixel[][] sourcePixArray, Pixel[][] upsampledPixArray){
		
		double left, right, top, bottom; //for bounding boxes
		Pixel t1a, t1b, t1c, t2a, t2b, t2c;
		Color A, B, C, D;
		int r,g,b;
		double dist1, dist2;
		
		for(int i = 2; i<sourcePixArray.length-1; i++){
			for(int j=2; j<sourcePixArray[0].length -1; j++){
				if(!jcb.isSelected()){
					
					//figure out how to divide quads
					dist1 = intensityDiff(sourcePixArray[i+1][j+1].getColor(),sourcePixArray[i][j].getColor()); //Math.pow((sourcePixArray[i+1][j+1].rx - sourcePixArray[i][j].rx),2) + Math.pow((sourcePixArray[i+1][j+1].ry - sourcePixArray[i][j].ry),2);
					dist2 = intensityDiff(sourcePixArray[i][j+1].getColor(),sourcePixArray[i+1][j].getColor()); //Math.pow((sourcePixArray[i][j+1].rx - sourcePixArray[i+1][j].rx),2) + Math.pow((sourcePixArray[i][j+1].ry - sourcePixArray[i+1][j].ry),2);
					t1a = sourcePixArray[i][j];
					t1c = sourcePixArray[i+1][j];
					t2b = sourcePixArray[i][j+1];
					t2c = sourcePixArray[i+1][j+1];
					if(dist1>dist2){
						t1b = sourcePixArray[i][j+1];
						t2a = sourcePixArray[i+1][j];
					}
					else{
						t1b = sourcePixArray[i+1][j+1];
						t2a = sourcePixArray[i][j];
					}
					
					left = sourcePixArray[i][j].rx<sourcePixArray[i+1][j].rx?sourcePixArray[i][j].rx:sourcePixArray[i+1][j].rx;
					right = sourcePixArray[i][j+1].rx>sourcePixArray[i+1][j+1].rx?sourcePixArray[i][j+1].rx:sourcePixArray[i+1][j+1].rx;
					top = sourcePixArray[i][j].ry<sourcePixArray[i][j+1].ry?sourcePixArray[i][j].ry:sourcePixArray[i][j+1].ry;
					bottom = sourcePixArray[i+1][j].ry>sourcePixArray[i+1][j+1].ry?sourcePixArray[i+1][j].ry:sourcePixArray[i+1][j+1].ry;
					
					for(int m = (int)top; m<=(int)bottom; m++){
						for(int n=(int)left; n<=(int)right; n++){
							Pixel temp = new Pixel();
							temp.rx = n;
							temp.ry = m;
							//System.out.println(" "+temp.rx+" "+temp.ry);
							//System.out.println(" "+"("+t1a.rx+","+t1a.ry+")"+"("+t1b.rx+","+t1b.ry+")"+"("+t1c.rx+","+t1c.ry+")"+"("+temp.rx+","+temp.ry+")"+(turns(t1a, t1b, temp)<=0)+" "+(turns(t1b,t1c,temp)<=0)+" "+(turns(t1c,t1a,temp)<=0));
							if(turns(t1a, t1b, temp)<=0&&turns(t1b,t1c,temp)<=0&&turns(t1c,t1a,temp)<=0){ //lies inside triangle1
								double combinedTriangleArea = triArea(t1a, t1b, t1c);
								r = (int)( t1a.getColor().getRed()*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.getColor().getRed()*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.getColor().getRed()*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								g = (int)( t1a.getColor().getGreen()*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.getColor().getGreen()*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.getColor().getGreen()*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								b = (int)( t1a.getColor().getBlue()*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.getColor().getBlue()*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.getColor().getBlue()*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								upsampledPixArray[m][n].textureness =  (int)( t1a.textureness*(triArea(temp,t1b,t1c)/combinedTriangleArea)
										+t1b.textureness*(triArea(temp,t1a,t1c)/combinedTriangleArea)
										+t1c.textureness*(triArea(temp,t1a,t1b)/combinedTriangleArea));
								Color tempC = new Color(r,g,b);
								upsampledPixArray[m][n].setColor(tempC);
							}
							else if(turns(t2a, t2b, temp)<=0&&turns(t2b,t2c,temp)<=0&&turns(t2c,t2a,temp)<=0){ //lies inside triangle2
								double combinedTriangleArea = triArea(t2a, t2b, t2c);
								r = (int)( t2a.getColor().getRed()*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.getColor().getRed()*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.getColor().getRed()*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								g = (int)( t2a.getColor().getGreen()*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.getColor().getGreen()*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.getColor().getGreen()*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								b = (int)( t2a.getColor().getBlue()*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.getColor().getBlue()*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.getColor().getBlue()*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								upsampledPixArray[m][n].textureness =  (int)( t2a.textureness*(triArea(temp,t2b,t2c)/combinedTriangleArea)
										+t2b.textureness*(triArea(temp,t2a,t2c)/combinedTriangleArea)
										+t2c.textureness*(triArea(temp,t2a,t2b)/combinedTriangleArea));
								Color tempC = new Color(r,g,b);
								upsampledPixArray[m][n].setColor(tempC);
							}
							else{
								//biFiltered.setRGB(n, m, getIntFromColor(Color.black));
							}
						}
					}
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
				else{
					addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i+1][j].rx), Tools.round(sourcePixArray[i+1][j].ry),Color.blue);
					addLine(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),Tools.round(sourcePixArray[i][j+1].rx), Tools.round(sourcePixArray[i][j+1].ry),Color.RED);
					//addPoint(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry),sourcePixArray[i][j].getColor(),12);
				}
			}
		}
	}
	
	static double turns (Pixel b, Pixel p, Pixel q){
		return (p.rx - b.rx)*(-q.ry + b.ry)-(-p.ry +b.ry)*(q.rx - b.rx);
	}
	
	static double triArea(Pixel t1, Pixel t2, Pixel t3){
		return Math.abs(0.5*(t1.rx*(t2.ry-t3.ry)+ t2.rx*(t3.ry-t1.ry)+ t3.rx*(t1.ry-t2.ry)));
	}
	
	static double cross(  Vec2 a,  Vec2 b ) { return a.x*b.y - a.y*b.x; }

	static Vec2 invBilinear( Vec2 p, Vec2 a, Vec2 b, Vec2 c, Vec2 d )
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
	
	private static Color[][] convertColorTo2DUsingGetRGB(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		Color[][] result = new Color[height][width];

		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				result[row][col] = new Color(image.getRGB(col, row));
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
	
	public static double signedIntensityDiff(Color c1, Color c2){
		double i1 = Math.sqrt(c1.getRed()*c1.getRed()+c1.getBlue()*c1.getBlue()+c1.getGreen()*c1.getGreen());
		double i2 = Math.sqrt(c2.getRed()*c2.getRed()+c2.getBlue()*c2.getBlue()+c2.getGreen()*c2.getGreen());
		return (i1-i2);
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
	
	public static void testing(){
		Pixel a = new Pixel();
		Pixel b = new Pixel();
		Pixel c = new Pixel();
		a.rx = 568; a.ry = 11;
		b.rx = 576; b.ry = 21;
		c.rx = 569; c.ry = 27;
		System.out.print(turns(a,b,c));
	}

	public static void main(String s[]) {
		//testing();
		f = new JFrame("Geodesic Filter & Upsampling");
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		
		
		// p1 = new JPanel();
		final Voronoi si = new Voronoi();
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