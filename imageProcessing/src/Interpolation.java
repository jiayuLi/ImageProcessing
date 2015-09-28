

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

public class Interpolation extends Component implements ActionListener {
	
	String descs[] = { "Original", "bicubic upsampled", "spring mass", "1D spring mass", "Length distribution", "Diagonal Spring", "angular spring",  "pre-filtering + angular"
			,"down-up, angular","springLengthSmoothing","gradient angular", "angular 13x13 smooth weak spring length", "texture enhanced", "textureness map", "non-upsample angular","random restLength"};

	//static String IMAGESOURCE = "testImg141219/all/24.jpg";
	static String IMAGESOURCE = "texim/dragonfly.png";
	//static String IMAGESOURCE = "landscape_ng.jpg";
	//static String IMAGESOURCE = "testImg141219/fattal/1.png";
	
	static String ORIGINALIMAGE = "redpicnictable.jpg"; //used for cross-filtering with original image

	//final int maskSize = 200;
	
	static double maskSize = 100;
	static int regionWH = (int) Math.sqrt(maskSize*16); //width or height of the sample region
	//final int upsampleMaskSize = 10;
	final double R = 1; //for geodesic
	static double gamma = 0.1; // for bilateral, for weight on intensity diff
	static double resizeScale = 8;
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
	
	//================================
	final int TIMESTEPS = 10;
	//=================================
	
	static boolean circleIsOn = false;
	static boolean showOriginalGrid = true;
	static boolean redoCalcFlag = false;
	static int offset1D = 0;
	static Pixel[][] sourcePixArrayFor1D;
	static double smoothingK = 1;
	static double GradientK = 0.1;

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

	public Interpolation() {
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
			w = bi.getWidth(null);
			h = bi.getHeight(null);
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
			
		
		case 2: { //spring mass
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixel(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			for(int i = 2; i<h-1; i++){
				for(int j=2; j<w-1; j++){
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
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 3:{//1D visulization of mass-spring system
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
			break;
		}
		case 4:{ //length distribution
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			for(int i = 2; i<h-1; i++){
				for(int j=2; j<w-1; j++){
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
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 5:{//diagonal springs
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
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
					}
				}
			}
			
			int timeStep = 0;
			
			while(timeStep<TIMESTEPS){
				//biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				timeStep++;
				Vec2 forceNorm;
				double moveDist;
				
				for(int i=0; i<h;i++){
					for(int j=0; j<w; j++){
						if(j<w-1&&i<h-1){
							double w1 = Math.abs(sourcePixArray[i][j].rx - sourcePixArray[i][j+1].rx);
							double w2 = Math.abs(sourcePixArray[i+1][j].rx - sourcePixArray[i+1][j+1].rx);
							double h1 = Math.abs(sourcePixArray[i][j].ry - sourcePixArray[i+1][j].ry);
							double h2 = Math.abs(sourcePixArray[i][j+1].ry - sourcePixArray[i+1][j+1].ry);
							sourcePixArray[i][j].restLengthC1 = Math.sqrt(Math.pow((w1+w2)*0.5,2)+Math.pow((h1+h2)*0.5,2));
						}
						else 
							sourcePixArray[i][j].restLengthC1 = resizeScale;
						if(j<w-1&&i>0){
							double w1 = Math.abs(sourcePixArray[i][j].rx - sourcePixArray[i][j+1].rx);
							double w2 = Math.abs(sourcePixArray[i-1][j].rx - sourcePixArray[i-1][j+1].rx);
							double h1 = Math.abs(sourcePixArray[i][j].ry - sourcePixArray[i-1][j].ry);
							double h2 = Math.abs(sourcePixArray[i][j+1].ry - sourcePixArray[i-1][j+1].ry);
							sourcePixArray[i][j].restLengthC2 = Math.sqrt(Math.pow((w1+w2)*0.5,2)+Math.pow((h1+h2)*0.5,2));
						}
						else
							sourcePixArray[i][j].restLengthC2 = resizeScale;
					}
				}
				
				for(int i = 2; i<(h-2); i++){
					//System.out.println();
					//System.out.print(" "+ i + " ");
					for(int j=2; j<(w-2); j++){
						Vec2 forceSum = new Vec2(0,0);
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelLengthDistribution(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcDiagonalSpringForce(sourcePixArray[i][j].restLengthC1, sourcePixArray[i][j], sourcePixArray[i+1][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcDiagonalSpringForce(sourcePixArray[i-1][j-1].restLengthC1, sourcePixArray[i][j], sourcePixArray[i-1][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcDiagonalSpringForce(sourcePixArray[i][j].restLengthC2, sourcePixArray[i][j], sourcePixArray[i-1][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcDiagonalSpringForce(sourcePixArray[i+1][j-1].restLengthC2, sourcePixArray[i][j], sourcePixArray[i+1][j-1]));
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
			
			for(int i = 2; i<h-1; i++){
				for(int j=2; j<w-1; j++){
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
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 6: { //angular spring
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		
		case 7: {//pre-filtering + angular
			
			/*
			//==============Geodesic===============================
			BufferedImage geoOrig = ImageIO.read(new File(IMAGESOURCE));
			int geoMaskSize = 15;
			int[][] intPixArrayGeo = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArrayGeo = convert2DIntArrayToPixelArray(intPixArrayGeo);
			//Pixel[][] resultPixArray = new Pixel[h][w];
			LinkedList<Pixel> pixStorage = new LinkedList<Pixel>();
			PriorityQueue<Pixel> maskPriorityQueue = new PriorityQueue<Pixel>();
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					
					
					// clean up storage & priority queue
					pixStorage.clear();
					maskPriorityQueue.clear();

					

					// start populating the mask
					sourcePixArrayGeo[i][j].setWeight(0);
					maskPriorityQueue.add(sourcePixArrayGeo[i][j]);
					int pixStorageCount = 0;
					while (pixStorageCount < geoMaskSize) {
						// get the lowest weighted pixel from the priority queue
						Pixel temp = maskPriorityQueue.remove();

						// find the four pixels adjacent to the selected pixel
						// the one on the left
						if (temp.getX() > 0) {
							Pixel left = sourcePixArrayGeo[temp.getY()][temp
									.getX() - 1];

							if (!left.isUsedByCurrentMask()) { // dr*dr +
																	// dg*dg +
																	// db*db
								double rangeDistance = intensityDiff(left.getColor(), sourcePixArrayGeo[i][j].getColor());
								double edgeCrossDistance = intensityDiff(left.getColor(), temp.getColor());
								double colorDistance = rangeDistance + R* edgeCrossDistance;
								
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
							Pixel right = sourcePixArrayGeo[temp.getY()][temp
									.getX() + 1];
							if (!right.isUsedByCurrentMask()) { // dr*dr + dg*dg
																// + db*db
								double rangeDistance = intensityDiff(right.getColor(), sourcePixArrayGeo[i][j].getColor());
								double edgeCrossDistance = intensityDiff(right.getColor(), temp.getColor());
								double colorDistance = rangeDistance + R* edgeCrossDistance;
								
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
							Pixel top = sourcePixArrayGeo[temp.getY() - 1][temp
									.getX()];
							if (!top.isUsedByCurrentMask()) { // dr*dr + dg*dg +
																// db*db
								double rangeDistance = intensityDiff(top.getColor(), sourcePixArrayGeo[i][j].getColor());
								double edgeCrossDistance = intensityDiff(top.getColor(), temp.getColor());
								double colorDistance = rangeDistance + R* edgeCrossDistance;
								
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
							Pixel bottom = sourcePixArrayGeo[temp.getY() + 1][temp
									.getX()];
							if (!bottom.isUsedByCurrentMask()) { // dr*dr +
																	// dg*dg +
																	// db*db
								double rangeDistance = intensityDiff(bottom.getColor(), sourcePixArrayGeo[i][j].getColor());
								double edgeCrossDistance = intensityDiff(bottom.getColor(), temp.getColor());
								double colorDistance = rangeDistance + R* edgeCrossDistance;
								
								
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

						pixStorage.add(temp);
						temp.setUsedByCurrentMask(true);
						pixStorageCount++;
					}

					// calculate the average color of the mask
					int sumR = 0, sumG = 0, sumB = 0;
					while (!pixStorage.isEmpty()) {
						Pixel tempPix = pixStorage.remove();
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

					geoOrig.setRGB(j, i, getIntFromColor(tempColor));
					
				}
				System.out.println("i" + i);
			}	
			//===========================================
			*/
			
			BufferedImage geoOrig = ImageIO.read(new File("texim/dragonfly_prefiltered.png"));
			int[][] intPixArrayGeo = convertTo2DUsingGetRGB(geoOrig);
			Pixel[][] sourcePixArrayGeo = convert2DIntArrayToPixelArray(intPixArrayGeo);
			
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArrayGeo[i][j].getColor(),sourcePixArrayGeo[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArrayGeo[i][j].getColor(),sourcePixArrayGeo[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			for(int i = 2; i<h-1; i++){
				for(int j=2; j<w-1; j++){
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
			
			//biFiltered = geoOrig;
			//biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 8:{//down-up angular
			//biFiltered = ImageIO.read(new File(IMAGESOURCE));
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			
			biOrig = resize(bi, (int) (w *(1/ resizeScale)),
					(int) (h *(1/ resizeScale)),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] intPixArray = convertTo2DUsingGetRGB(biOrig);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = biOrig.getWidth(null);
			h = biOrig.getHeight(null);
			
			biFiltered = resize(biOrig, (int) (w * resizeScale),
					(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			
			//populate rx ry, original pixel's real position on upsampled image
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 9:{//angular spring length smoothing
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
					}
				}
			}
			
			//pre-calc spring length using distribution
			preCalcSpringLengthUsingDistribution(sourcePixArray);
			//pre-calc magnitude of gradient
			preCalcMoG(sourcePixArray);
			//smooth spring length 
			smoothSpringLength(sourcePixArray);
			
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSmoothLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSmoothLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSmoothLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSmoothLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 10: {//gradient angular
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
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = signedIntensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = signedIntensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
					}
				}
			}
			
			//pre-calc gradient
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH+sourcePixArray[i][j-1].deltaH, sourcePixArray[i][j].deltaV+sourcePixArray[i-1][j].deltaV);
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringGradient(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringGradient(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringGradient(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringGradient(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 11: {//angular 13x13 smooth weak length
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt13x13(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt13x13(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt13x13(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt13x13(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		case 12: { //textureness
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			//biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
				MoG = new double[h][w];
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
						
						sourcePixArray[i][j].setWeight(MoG[i][j]); 
						//System.out.print(" "+ temp);
					}
				}
			}
			
			//calc textureness
			int neighborSize = 10;
			neighborSize/=2;
			PriorityQueue<Pixel> neighborhood = new PriorityQueue<Pixel>();
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					neighborhood.clear();
					//populate neighborhood mask
					for(int m=i-neighborSize;m<i+neighborSize;m++ ){
						for(int n=j-neighborSize;n<j+neighborSize;n++){
							if(m>=0&&n>=0&&m<h-1&&n<w-1){
								neighborhood.add(sourcePixArray[m][n]);
							}
						}
					}
					//find 30% median
					int counter = 0;
					while (counter<=neighborhood.size()*0.3){
						neighborhood.poll();
						counter++;
					}
					sourcePixArray[i][j].textureness= neighborhood.peek().getWeight();
					/*
					if(sourcePixArray[i][j].textureness>50)
						biFiltered.setRGB(j, i, getIntFromColor(Color.red));
					else if(sourcePixArray[i][j].textureness>30)
						biFiltered.setRGB(j, i, getIntFromColor(Color.orange));
					else if(sourcePixArray[i][j].textureness>20)
						biFiltered.setRGB(j, i, getIntFromColor(Color.yellow));
					else if(sourcePixArray[i][j].textureness>10)
						biFiltered.setRGB(j, i, getIntFromColor(Color.green));
					else if(sourcePixArray[i][j].textureness>0)
						biFiltered.setRGB(j, i, getIntFromColor(Color.blue));
					*/
				}
				System.out.println("texture i"+i);
			}//end textureness calc
			
			
			
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
				}
			}
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringSuccessAttempt(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
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
			biFiltered = resize(bi, (int) (w * resizeScale),(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int[][] upsampledintPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] upsampledSourcePixArray = convert2DIntArrayToPixelArray(upsampledintPixArray);
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentricTexture(sourcePixArray, upsampledSourcePixArray);
			
			Texture t1 = new Texture("textures/paintwall.jpg");
			System.out.println("done processing texture img");
			
			int r,g,b;
			
			for(int i=0; i<upsampledSourcePixArray.length;i++){
				for (int j=0; j<upsampledSourcePixArray[0].length; j++){
					r = upsampledSourcePixArray[i][j].getColor().getRed()+(int)(t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].red*texturenessStrength*upsampledSourcePixArray[i][j].textureness);
					g = upsampledSourcePixArray[i][j].getColor().getGreen()+(int)(t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].green*texturenessStrength*upsampledSourcePixArray[i][j].textureness);
					b = upsampledSourcePixArray[i][j].getColor().getBlue()+(int)(t1.getTexture()[i%t1.getHeight()][j%t1.getWidth()].blue*texturenessStrength*upsampledSourcePixArray[i][j].textureness);
					r = r>255?255:r; r = r<0?0:r;
					g = g>255?255:g; g = g<0?0:g;
					b = b>255?255:b; b = b<0?0:b;
					//upsampledSourcePixArray[i][j].setColor(new Color(r,g,b));
					biFiltered.setRGB(j, i, getIntFromColor(new Color(r,g,b)));
				}
			}
			break;
		}
		case 13:{//textureness map
			System.out.print("blablabla");
		}
		
		case 14:{//angular strong weak length
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringStrongSmoothLinks(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringStrongSmoothLinks(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringStrongSmoothLinks(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringStrongSmoothLinks(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
						//forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenPixelCurrentAndOriginal(sourcePixArray[i][j]));
						
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		
		case 15:{//random length
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
			
			
			//pre-calc neighborhood intensity difference
			for(int i=0; i<h;i++){
				for(int j=0; j<w; j++){
					if(j<w-1){
						sourcePixArray[i][j].deltaH = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i][j+1].getColor());
					}
					if(i<h-1){
						sourcePixArray[i][j].deltaV = intensityDiff(sourcePixArray[i][j].getColor(),sourcePixArray[i+1][j].getColor());
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringRandomLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringRandomLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringRandomLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelAngularSpringRandomLength(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
						//forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenPixelCurrentAndOriginal(sourcePixArray[i][j]));
						
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
			
			//interpolation
			//inverseBilinear(sourcePixArray);
			triangleBarycentric(sourcePixArray);
			//bounding box
			
			
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			repaint();
			break;
		}
		}
	}
	
	private Vec2 calcForceBetweenTwoPixelAngularSpringSuccessAttempt13x13(
			Pixel[][] sourcePixArray, Pixel center, Pixel out) {
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		//double sumNeighborhoodWeight = 0.0;
		double strongerNeighborSpringCount = 0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-10; i<topleftPix.getY()+11; i++){
			for(int j= topleftPix.getX()-10; j<topleftPix.getX()+11; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
					if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
						strongerNeighborSpringCount++;
					if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
						strongerNeighborSpringCount++;
				}
			}
		}
		
		//double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		/*
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		*/
		double ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
		if (ranking <= 0.15)
			linkSpringforce = (displacement.getLength() - 1) * springK;
		else if (ranking <= 0.6){
			double restLength = 1 + (resizeScale-1)*(ranking-0.15)/0.45;
			linkSpringforce = (displacement.getLength() - restLength) * springK;
		}
		else{
			double restLength = resizeScale + 0.5*(resizeScale)*(ranking-0.6)/0.4;
			linkSpringforce = (displacement.getLength()-restLength)*springK;
		}
			
		linkSpringforce -= linkSpringforce * damperC;
		
		Vec2 angularSpringForce = new Vec2(0,0);
		//find out if we are dealing with vertical or horizontal springs
		if(center.getX()==out.getX()){//vertical
			if(center.rx == out.rx){
				angularSpringForce = new Vec2(0,0);
			}
			else {
				double angle = Math.atan(direction.x/direction.y);
				angularSpringForce = new Vec2 (direction.y*angle*angularSpringK, -direction.x*angle*angularSpringK);
			}
		}
		else if(center.getY() == out.getY()){//horizontal
			if(center.ry == out.ry){
				angularSpringForce = new Vec2(0,0);
			}
			else{
				double angle = Math.atan(direction.y/direction.x);
				angularSpringForce = new Vec2 (-direction.y*angle*angularSpringK, direction.x*angle*angularSpringK);
			}
		}
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
	}

	private Vec2 calcForceBetweenTwoPixelAngularSpringGradient(Pixel[][] sourcePixArray, Pixel center, Pixel out) {
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		//double sumNeighborhoodWeight = 0.0;
		double strongerNeighborSpringCount = 0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
					if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
						strongerNeighborSpringCount++;
					if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
						strongerNeighborSpringCount++;
				}
			}
		}
		
		//double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		/*
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		*/
		double ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
		if (ranking <= 0.15)
			linkSpringforce = (displacement.getLength() - 1) * springK;
		else if (ranking <= 0.6){
			double restLength = 1 + (resizeScale-1)*(ranking-0.15)/0.45;
			linkSpringforce = (displacement.getLength() - restLength) * springK;
		}
		else
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		linkSpringforce -= linkSpringforce * damperC;
		Vec2 angularSpringForce = new Vec2(0.0,0.0);
		///*
		angularSpringForce = new Vec2((center.gradient.x+out.gradient.x)*0.5*GradientK,(center.gradient.y+out.gradient.y)*0.5*GradientK);
		//find out the angle between spring's current orientation and the avg gradient
		Vec2 currentOrientation = new Vec2(center.rx - out.rx, center.ry - out.ry);
		double theta = angleBetweenTwoVector(currentOrientation, angularSpringForce);
		//System.out.println(theta);
		if(theta<(3.1415926*0.5))
			angularSpringForce.negate();
		
		angularSpringForce.scale(Math.abs(theta-(3.1415926*0.5)));
		//*/
		
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
	}

	public static double angleBetweenTwoVector(Vec2 v1, Vec2 v2) {
		if(v1.getLength()==0||v2.getLength()==0)
			return 3.1415926*0.5;
		else
			return Math.acos((v1.x*v2.x + v1.y*v2.y)/(v1.getLength()*v2.getLength()));
	}

	private Vec2 calcForceBetweenTwoPixelSmoothLength(Pixel[][] sourcePixArray, Pixel center, Pixel out) {
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//Horizontal/Vertical
		int neighborhoodSpringCount = 0;
		double linkSpringforce;
		double strongerNeighborSpringCount = 0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
			if(topleftPix.restLengthV<=resizeScale)
				linkSpringforce = (displacement.getLength() - topleftPix.restLengthV) * springK;
			else
				linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		else{
			topleftPix = center.getX()<out.getX()?center:out;
			if(topleftPix.restLengthH<=resizeScale)
				linkSpringforce = (displacement.getLength() - topleftPix.restLengthH) * springK;
			else
				linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		
		//angular
		Vec2 angularSpringForce = new Vec2(0,0);
		//find out if we are dealing with vertical or horizontal springs
		if(center.getX()==out.getX()){//vertical
			if(center.rx == out.rx){
				angularSpringForce = new Vec2(0,0);
			}
			else {
				double angle = Math.atan(direction.x/direction.y);
				angularSpringForce = new Vec2 (direction.y*angle*angularSpringK, -direction.x*angle*angularSpringK);
			}
		}
		else if(center.getY() == out.getY()){//horizontal
			if(center.ry == out.ry){
				angularSpringForce = new Vec2(0,0);
			}
			else{
				double angle = Math.atan(direction.y/direction.x);
				angularSpringForce = new Vec2 (-direction.y*angle*angularSpringK, direction.x*angle*angularSpringK);
			}
		}
		//System.out.println(""+ (direction.x*linkSpringforce+angularSpringForce.x) +"  " + (direction.y*linkSpringforce+angularSpringForce.y) );
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
	}

	private void smoothSpringLength(Pixel[][] sourcePixArray) {
		double[][] gaussianKernel = new double[][]{
				{1.0/273, 4.0/273, 7.0/273, 4.0/273, 1.0/273},
				{4.0/273, 16.0/273, 26.0/273, 16.0/273, 4.0/273}, 
				{7.0/273, 26.0/273, 41.0/273, 26.0/273, 7.0/273}, 
				{4.0/273, 16.0/273, 26.0/273, 16.0/273, 4.0/273}, 
				{1.0/273, 4.0/273, 7.0/273, 4.0/273, 1.0/273}
		};
		
		double[][] smoothedRLH = new double[sourcePixArray.length][sourcePixArray[0].length];
		double[][] smoothedRLV = new double[sourcePixArray.length][sourcePixArray[0].length];
		
		for(int p = 2; p<sourcePixArray.length-2;p++){
			for(int q = 2; q<sourcePixArray[0].length-2; q++){
				//horizontal
				for(int i = 0; i<gaussianKernel.length; i++){
					for(int j =0; j<gaussianKernel[0].length;j++){
						smoothedRLH[p][q]+= sourcePixArray[p-2+i][q-2+j].restLengthH * gaussianKernel[i][j];
					}
				}
				//sourcePixArray[p][q].restLengthH += (smoothedRL-sourcePixArray[p][q].restLengthH)*smoothingK/(MoG[p][q]+MoG[p][q+1])*0.5;
				//vertical
				for(int i = 0; i<gaussianKernel.length; i++){
					for(int j =0; j<gaussianKernel[0].length;j++){
						smoothedRLV[p][q]+= sourcePixArray[p-2+i][q-2+j].restLengthV * gaussianKernel[i][j];
					}
				}
				//sourcePixArray[p][q].restLengthV += (smoothedRL-sourcePixArray[p][q].restLengthV)*smoothingK/(MoG[p][q]+MoG[p][q+1])*0.5;
			}
		}
		
		for(int p = 2; p<sourcePixArray.length-2;p++){
			for(int q = 2; q<sourcePixArray[0].length-2; q++){
				sourcePixArray[p][q].restLengthH += (smoothedRLH[p][q]-sourcePixArray[p][q].restLengthH)*smoothingK/(MoG[p][q]+MoG[p][q+1]+1)*0.5;
				sourcePixArray[p][q].restLengthV += (smoothedRLV[p][q]-sourcePixArray[p][q].restLengthV)*smoothingK/(MoG[p][q]+MoG[p][q+1]+1)*0.5;
			}
		}
		
	}

	private void preCalcMoG(Pixel[][] sourcePixArray) {
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
			MoG = new double[sourcePixArray.length][sourcePixArray[0].length];
			for (int i = 0; i<sourcePixArray.length; i++){
				for(int j=0; j<sourcePixArray[0].length; j++){
					
					double GradSum = 0;
					for(int ki = 0; ki<logKernel.length; ki++){
						for(int kj =0; kj<logKernel[0].length;kj++){
							if(i<2||i>(logKernel.length-3)||j<2||j>(sourcePixArray[0].length-3)){
							}
							else
								GradSum+= sourcePixArray[i-2+ki][j-2+kj].getIntensity() * logKernel[ki][kj];
						}
					}
					MoG[i][j] = Math.abs((GradSum));
					//System.out.println(MoG[i][j]);
				}
			}
		}
	}

	private void preCalcSpringLengthUsingDistribution(Pixel[][] sourcePixArray) {
		for(int p = 2; p<sourcePixArray.length-2;p++){
			for(int q = 2; q<sourcePixArray[0].length-2; q++){
				//horizontal
				double localWeight = 1/(2+intensityDiff(sourcePixArray[p][q].getColor(),sourcePixArray[p][q+1].getColor()));
				int neighborhoodSpringCount = 0;
				double strongerNeighborSpringCount = 0;
				
				for(int i = p-2; i<p+3; i++){
					for(int j= q-2; j<q+3; j++){
						if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
							neighborhoodSpringCount+=2;
							//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
							//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
							if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
								strongerNeighborSpringCount++;
							if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
								strongerNeighborSpringCount++;
						}
					}
				}
				
				double ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
				if (ranking <= 0.15)
					sourcePixArray[p][q].restLengthH = 1;
				else if (ranking <= 0.6){
					sourcePixArray[p][q].restLengthH = 1 + (resizeScale-1)*(ranking-0.15)/0.45;
				}
				else
					sourcePixArray[p][q].restLengthH  = resizeScale + 1;
				
				//vertical
				localWeight = 1/(2+intensityDiff(sourcePixArray[p][q].getColor(),sourcePixArray[p+1][q].getColor()));
				neighborhoodSpringCount = 0;
				strongerNeighborSpringCount = 0;
				
				for(int i = p-2; i<p+3; i++){
					for(int j= q-2; j<q+3; j++){
						if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
							neighborhoodSpringCount+=2;
							//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
							//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
							if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
								strongerNeighborSpringCount++;
							if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
								strongerNeighborSpringCount++;
						}
					}
				}
				
				ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
				if (ranking <= 0.15)
					sourcePixArray[p][q].restLengthV = 1;
				else if (ranking <= 0.6){
					sourcePixArray[p][q].restLengthV = 1 + (resizeScale-1)*(ranking-0.15)/0.45;
				}
				else
					sourcePixArray[p][q].restLengthV  = resizeScale + 1;
			}
		}
	}

	Vec2 calcforce1D(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		
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
	
	
	private static Vec2 calcForceBetweenTwoPixel(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(50+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		double sumNeighborhoodWeight = 0.0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					sumNeighborhoodWeight+= 1/(50+sourcePixArray[i][j].deltaH);
					sumNeighborhoodWeight+= 1/(50+sourcePixArray[i][j].deltaV);
				}
			}
		}
		
		double localk = localWeight/sumNeighborhoodWeight;
		double force;
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			force = (displacement.getLength()-resizeScale)*springK/3;
		}
		else{
			force = (displacement.getLength()-resizeScale*localk*sumNeighborhoodWeight)*springK;
		}
		force -= force * damperC;
		return new Vec2(direction.x*force, direction.y*force);
	}
	
	/*
	private static Vec2 calcForceBetweenTwoPixelAngularSpring(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(1+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		double sumNeighborhoodWeight = 0.0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					sumNeighborhoodWeight+= 1/(1+sourcePixArray[i][j].deltaH);
					sumNeighborhoodWeight+= 1/(1+sourcePixArray[i][j].deltaV);
				}
			}
		}
		
		double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		else{
			linkSpringforce = (displacement.getLength()-resizeScale*localk*sumNeighborhoodWeight)*springK;
		}
		linkSpringforce -= linkSpringforce * damperC;
		
		Vec2 angularSpringForce = new Vec2(0,0);
		//find out if we are dealing with vertical or horizontal springs
		if(center.getX()==out.getX()){//vertical
			if(center.rx == out.rx){
				angularSpringForce = new Vec2(0,0);
			}
			else {
				double angle = Math.atan(direction.x/direction.y);
				angularSpringForce = new Vec2 (direction.y*angle*angularSpringK, -direction.x*angle*angularSpringK);
			}
		}
		else if(center.getY() == out.getY()){//horizontal
			if(center.ry == out.ry){
				angularSpringForce = new Vec2(0,0);
			}
			else{
				double angle = Math.atan(direction.y/direction.x);
				angularSpringForce = new Vec2 (-direction.y*angle*angularSpringK, direction.x*angle*angularSpringK);
			}
		}
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
	}
	*/
	
	private static Vec2 calcForceBetweenTwoPixelAngularSpringSuccessAttempt(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		//double sumNeighborhoodWeight = 0.0;
		double strongerNeighborSpringCount = 0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
					if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
						strongerNeighborSpringCount++;
					if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
						strongerNeighborSpringCount++;
				}
			}
		}
		
		//double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		/*
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		*/
		double ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
		if (ranking <= 0.15)
			linkSpringforce = (displacement.getLength() - 0.1) * springK;
		else if (ranking <= 0.6){
			double restLength = 0.1 + (resizeScale-0.1)*(ranking-0.15)/0.45;
			linkSpringforce = (displacement.getLength() - restLength) * springK;
		}
		else
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		linkSpringforce -= linkSpringforce * damperC;
		
		Vec2 angularSpringForce = new Vec2(0,0);
		//find out if we are dealing with vertical or horizontal springs
		if(center.getX()==out.getX()){//vertical
			if(center.rx == out.rx){
				angularSpringForce = new Vec2(0,0);
			}
			else {
				double angle = Math.atan(direction.x/direction.y);
				angularSpringForce = new Vec2 (direction.y*angle*angularSpringK, -direction.x*angle*angularSpringK);
			}
		}
		else if(center.getY() == out.getY()){//horizontal
			if(center.ry == out.ry){
				angularSpringForce = new Vec2(0,0);
			}
			else{
				double angle = Math.atan(direction.y/direction.x);
				angularSpringForce = new Vec2 (-direction.y*angle*angularSpringK, direction.x*angle*angularSpringK);
			}
		}
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
	}
	
	private static Vec2 calcForceBetweenTwoPixelAngularSpringStrongSmoothLinks(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		//double sumNeighborhoodWeight = 0.0;
		double strongerNeighborSpringCount = 0;
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
					if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
						strongerNeighborSpringCount++;
					if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
						strongerNeighborSpringCount++;
				}
			}
		}
		
		//double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		/*
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		*/
		double ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
		if (ranking >= 0.85)
			linkSpringforce = (displacement.getLength() - 10) * springK;
		else if (ranking >= 0.40){
			double restLength = 10 + (resizeScale-10)*(0.85-ranking)/0.45;
			linkSpringforce = (displacement.getLength() - restLength) * springK;
		}
		else
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		linkSpringforce -= linkSpringforce * damperC;
		
		//return new Vec2(direction.x*linkSpringforce, direction.y*linkSpringforce);
		
		Vec2 angularSpringForce = new Vec2(0,0);
		//find out if we are dealing with vertical or horizontal springs
		if(center.getX()==out.getX()){//vertical
			if(center.rx == out.rx){
				angularSpringForce = new Vec2(0,0);
			}
			else {
				double angle = Math.atan(direction.x/direction.y);
				angularSpringForce = new Vec2 (direction.y*angle*angularSpringK, -direction.x*angle*angularSpringK);
			}
		}
		else if(center.getY() == out.getY()){//horizontal
			if(center.ry == out.ry){
				angularSpringForce = new Vec2(0,0);
			}
			else{
				double angle = Math.atan(direction.y/direction.x);
				angularSpringForce = new Vec2 (-direction.y*angle*angularSpringK, direction.x*angle*angularSpringK);
			}
		}
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
		
	}
	
	private static Vec2 calcForceBetweenTwoPixelAngularSpringRandomLength(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double restLength = 2*(1+rand(new Vec2(center.getX()+out.getX(),center.getY()+out.getY())));
		
		//double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce = (displacement.getLength() - restLength) * springK;;
		/*
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		*/
		//return new Vec2(direction.x*linkSpringforce, direction.y*linkSpringforce);
		
		Vec2 angularSpringForce = new Vec2(0,0);
		//find out if we are dealing with vertical or horizontal springs
		if(center.getX()==out.getX()){//vertical
			if(center.rx == out.rx){
				angularSpringForce = new Vec2(0,0);
			}
			else {
				double angle = Math.atan(direction.x/direction.y);
				angularSpringForce = new Vec2 (direction.y*angle*angularSpringK, -direction.x*angle*angularSpringK);
			}
		}
		else if(center.getY() == out.getY()){//horizontal
			if(center.ry == out.ry){
				angularSpringForce = new Vec2(0,0);
			}
			else{
				double angle = Math.atan(direction.y/direction.x);
				angularSpringForce = new Vec2 (-direction.y*angle*angularSpringK, direction.x*angle*angularSpringK);
			}
		}
		return new Vec2(direction.x*linkSpringforce+angularSpringForce.x, direction.y*linkSpringforce+angularSpringForce.y);
		
	}
	
	static double rand(Vec2 co){
	    return fract(Math.sin(Vec2.dot(co , new Vec2(12.9898,78.233))) * 43758.5453);
	}
	
	static double fract(double a){
		return a-(int)a;
	}
	
	private static Vec2 calcForceBetweenTwoPixelLengthDistribution(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight;
		int neighborhoodSpringCount = 0;
		//double sumNeighborhoodWeight = 0.0;
		double strongerNeighborSpringCount = 0.0;
		
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
			localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		}
		else {
			topleftPix = center.getX()<out.getX()?center:out;
			localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		}
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
					//sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
					if((1/(2+sourcePixArray[i][j].deltaH))<=localWeight)
						strongerNeighborSpringCount++;
					if((1/(2+sourcePixArray[i][j].deltaV))<=localWeight)
						strongerNeighborSpringCount++;
				}
			}
		}
		
		//double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		/*
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		*/
		double ranking = strongerNeighborSpringCount / neighborhoodSpringCount;
		if (ranking <= 0.15)
			linkSpringforce = (displacement.getLength() - 1) * springK;
		else if (ranking <= 0.6){
			double restLength = 1 + (resizeScale-1)*(ranking-0.15)/0.45;
			linkSpringforce = (displacement.getLength() - restLength) * springK;
		}
		else
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		linkSpringforce -= linkSpringforce * damperC;
		
		return new Vec2(direction.x*linkSpringforce, direction.y*linkSpringforce);
	}
	
	private static Vec2 calcDiagonalSpringForce(double restLength, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		double diagonalSpringforce = (displacement.getLength()-restLength)*springK;
		diagonalSpringforce -= diagonalSpringforce * damperC;
		return new Vec2(direction.x*diagonalSpringforce, direction.y*diagonalSpringforce);
		
	}
	
	/*
	private static Vec2 calcForceBetweenTwoPixelLengthDistribution(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		//
		double localWeight = 1/(2+intensityDiff(center.getColor(),out.getColor()));
		int neighborhoodSpringCount = 0;
		double sumNeighborhoodWeight = 0.0;
		int strongerNeighborSpringCount = 0;
		
		Pixel topleftPix;
		if(center.getX()==out.getX()){
			topleftPix = center.getY()<out.getY()?center:out;
		}
		else
			topleftPix = center.getX()<out.getX()?center:out;
		
		for(int i = topleftPix.getY()-2; i<topleftPix.getY()+3; i++){
			for(int j= topleftPix.getX()-2; j<topleftPix.getX()+3; j++){
				if(i>=0&&i<sourcePixArray.length-2&&j>=0&&(j<sourcePixArray[0].length-2)){
					neighborhoodSpringCount+=2;
					sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaH);
					sumNeighborhoodWeight+= 1/(2+sourcePixArray[i][j].deltaV);
					if((1/(2+sourcePixArray[i][j].deltaH))<localWeight)
						strongerNeighborSpringCount++;
					if((1/(2+sourcePixArray[i][j].deltaV))<localWeight)
						strongerNeighborSpringCount++;
				}
			}
		}
		
		double localk = localWeight/sumNeighborhoodWeight;
		double linkSpringforce;
		if(localk>(1.0/neighborhoodSpringCount)){ //weak link
			linkSpringforce = (displacement.getLength()-resizeScale)*springK/3;
		}
		else{
			//force = (displacement.getLength()-resizeScale*localk*sumNeighborhoodWeight)*springK;
			if(strongerNeighborSpringCount/neighborhoodSpringCount<=0.15)
				linkSpringforce = (displacement.getLength()-1)*springK;
			else if(strongerNeighborSpringCount/neighborhoodSpringCount<=0.3)
				linkSpringforce = (displacement.getLength()-0.25*resizeScale)*springK;
			else if(strongerNeighborSpringCount/neighborhoodSpringCount<=0.45)
				linkSpringforce = (displacement.getLength()-0.50*resizeScale)*springK;
			else
				linkSpringforce = (displacement.getLength()-resizeScale)*springK;
		}
		linkSpringforce -= linkSpringforce * damperC;
		return new Vec2(direction.x*linkSpringforce, direction.y*linkSpringforce);
	}
	*/
	
	
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
								Color tempC = new Color(r,g,b);
								r=r>255?255:r;
								g=g>255?255:g;
								b=b>255?255:b;
								n=n<0?0:n;
								m=m<0?0:m;
								n=n>w*(int)resizeScale-1?w*(int)resizeScale-1:n;
								m=m>h*(int)resizeScale-1?h*(int)resizeScale-1:m;
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
								Color tempC = new Color(r,g,b);
								r=r>255?255:r;
								g=g>255?255:g;
								b=b>255?255:b;
								n=n<0?0:n;
								m=m<0?0:m;
								n=n>w*(int)resizeScale-1?w*(int)resizeScale-1:n;
								m=m>h*(int)resizeScale-1?h*(int)resizeScale-1:m;
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
		
		System.out.print("Done interpolation");
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
	
	public static void testing(){
		for(int i =0; i<10;i++){
			System.out.println(""+rand(new Vec2(i,i)));
		}
	}

	public static void main(String s[]) {
		testing();
		f = new JFrame("Geodesic Filter & Upsampling");
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		
		
		// p1 = new JPanel();
		final Interpolation si = new Interpolation();
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