

import java.io.*;
import java.util.ArrayList;
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

public class L0_visualization extends Component implements ActionListener {
	
	String descs[] = { "L0","Original" ,"bicubic upsampled",  "1D spring mass" , "BoundaryTracingVisualization", "BTV_current&neighbors", "BTV_current&neighbors_normalize"
			,"region merging","tracing on upsampled"};

	//===========================================
	static String IMAGESOURCE = "testImgL0/pflower_kappa1.png";
	static String L0original = "testImgL0/pflower.jpg";
	//=============================================
	//static String IMAGESOURCE = "testImgL0/bird.png";
	//static String L0original = "testImgL0/5.jpg";
	//================================================
	//static String IMAGESOURCE = "testImgL0/L0dragonfly.png";
	//static String L0original = "testImgL0/dragonfly.png";
	//================================================
	static String ORIGINALIMAGE = "redpicnictable.jpg"; //used for cross-filtering with original image

	//final int maskSize = 200;
	
	static double maskSize = 100;
	static int regionWH = (int) Math.sqrt(maskSize*16); //width or height of the sample region
	//final int upsampleMaskSize = 10;
	final double R = 1; //for geodesic
	static double gamma = 0.1; // for bilateral, for weight on intensity diff
	static int resizeScale = 3;
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
	
	//===============================
	final int LABELTHRESHOLD = 1;
	final int MERGESIZE = 4;
	//=========================
	
	//for mass-spring
	final static double springK = 1;
	final static double damperC = 0.5;
	final int TIMESTEPS = 500;
	static boolean circleIsOn = false;
	static boolean showOriginalGrid = true;
	static boolean redoCalcFlag = false;
	static int offset1D = 0;
	static Pixel[][] sourcePixArrayFor1D;
	static Pixel[][] sourcePixArrayForOrig;
	static Pixel[][] sourcePixArrayUpsampled;

	static int opIndex;
	static int lastOp;
	private static BufferedImage bi;
	private static BufferedImage L0orig;// = ImageIO.read(new File(L0original));
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

	public L0_visualization() {
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
			/*g.setColor(Color.white);
			g.fillRect(histogramStartX-5, histogramStartY-80, 275, 635);
			g.setColor(Color.black);
			g.drawString("0", histogramStartX, histogramStartY+10);
			g.drawString("256", histogramStartX+250, histogramStartY+10);
			g.drawString("blue: input", histogramStartX+10, histogramStartY-65);
			g.drawString("green: output", histogramStartX+10, histogramStartY-50);
			g.drawString("red: mode", histogramStartX+10, histogramStartY-35);
			g.drawString("closest outside-mask pixels", histogramStartX+10, histogramStartY+35);
			*/
			if(mouseX!=0 || mouseY!=0){
				g.setColor(Color.red);
				g.drawLine(mouseX+0, mouseY, mouseX+50, mouseY);
				g.drawLine(mouseX-0, mouseY, mouseX-50, mouseY);
				g.drawLine(mouseX, mouseY-0, mouseX, mouseY-50);
				g.drawLine(mouseX, mouseY+0, mouseX, mouseY+50);
				g.setColor(Color.black);
			}
			/*
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
			return;
		
		case 1:{
			bi = ImageIO.read(new File(L0original));
			biFiltered = bi; /* original */
			return;
		}

		case 2: /*bicubic upsampled */{
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = blockyUpsampling(bi);
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
		case 4:{//boundary tracing visualization
			bi = ImageIO.read(new File(IMAGESOURCE));
			if(redoCalcFlag==true){
				int[][] intPixArray = convertTo2DUsingGetRGB(bi);
				sourcePixArrayFor1D = convert2DIntArrayToPixelArray(intPixArray);
				w = bi.getWidth(null);
				h = bi.getHeight(null);
				biFiltered = ImageIO.read(new File(IMAGESOURCE));
			}
			
			BufferedImage L0orig = ImageIO.read(new File(L0original));
			int[][] intPixArrayForOrig = convertTo2DUsingGetRGB(L0orig);
			sourcePixArrayForOrig = convert2DIntArrayToPixelArray(intPixArrayForOrig);
			
			
			int Xorig = 0;
			int Yorig = 800;
			
			if (mouseX >= w || mouseY >= h) {
				jtf.setText("mouse location exceed image boundaries");
			}
			else{
				
				
				if(redoCalcFlag==true){
					//start L0 calculation
					//find segment
					
					//reset label
					for(int i=1; i<h-1;i++){
						for(int j=1; j<w-1; j++){
							//sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
							//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
							sourcePixArrayFor1D[i][j].label = new Pair(-1,-1);
						}
					}
					
					//int maxPixelCount = w * h;
					//int currPixelCount = 0;
					int labelCount = 0;
					Pixel currPix = sourcePixArrayFor1D[mouseY][mouseX];
					ArrayList<Pixel> region = new ArrayList<Pixel>();
					ArrayList<Pair> tracing = new ArrayList<Pair>();
					
					region.clear();
					tracing.clear();
					region.add(currPix);
					Random r = new Random();
					Color tempColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					Color boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					//finding region
					while(!region.isEmpty()){
						Pixel temp = region.remove(0);
						tracing.add(new Pair(temp.getX(),temp.getY()));
						temp.label.a = labelCount;
						if(temp.getX()>0&&sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArrayFor1D[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()-1]);
						}
						if(temp.getX()<(w-1)&&sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()+1]);
						}
						if(temp.getY()>0&&sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()-1][temp.getX()]);
						}
						if(temp.getY()<(h-1)&&sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()+1][temp.getX()]);
						}
						//currPixelCount++;
						biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
					}
					
					//tracing
					//boundary tracing
					int boundaryLabel = 0;
					//int tracingCount = 0;
					while(true){
						//find the starting point -- top left
						Pair start = tracing.get(0);
						//System.out.print("default start a" + start.a + "b" + start.b);
						
						for(int i=0; i<tracing.size();i++){
							//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								start = tracing.get(i);
								break;
								//System.out.println("start changed?");
							}
						}
						
						if(sourcePixArrayFor1D[start.b][start.a].label.b!=-1){
							System.out.print("broke from can't find new start");
							break;
						}
						//System.out.print("not broke, start found");
						
						for(int i=0; i<tracing.size();i++){
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								if(tracing.get(i).b<start.b){
									start = tracing.get(i); System.out.print("start updated");
								}
								else if(tracing.get(i).b == start.b && tracing.get(i).a<start.a){
									start = tracing.get(i); System.out.print("start updated");
								}
									
								else{
									//System.out.print("start notupdated");
								}
							}
						}
						System.out.println("start at a"+start.a+"b"+start.b);
						
						//label start
						sourcePixArrayFor1D[start.b][start.a].label.b = boundaryLabel;
						System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[start.b][start.a].label.a +"b" + sourcePixArrayFor1D[start.b][start.a].label.b );
						//tracingCount++;
						System.out.println(" coord" + start.a + " " + start.b);
						Color signal1DColor = tempColor;
						boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
						biFiltered.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
						signal1DColor = boundaryColor;
						addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[start.b][start.a].getIntensity()), signal1DColor, 3);
						
						//tracingCount++;
						Pair curr = new Pair(start.a, start.b);
						int dir = 7;
						int pixCount = 1;
						boolean foundNext = false;
						do{
							int inc = 0;
							
							//search next
							foundNext = false;
							while(inc<8){
								Pair check = new Pair(0,0);
								
								if(dir%2==0){
									check = L0.dirLookup(dir+7+inc);
								}
								else
									check = L0.dirLookup(dir+6+inc);
								
								check = new Pair(check.a+curr.a, check.b+curr.b);
								
								if(check.b>=0&&check.a>=0&&check.a<=w-1&&check.b<=h-1){
									if(check.a == start.a&&check.b == start.b){
										foundNext = false;
										break;
									}
									
									if((sourcePixArrayFor1D[check.b][check.a].label.b==-1||sourcePixArrayFor1D[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayFor1D[check.b][check.a].label.a==labelCount){
										//next found
										foundNext = true;
										//paint 1D signal
										pixCount++;
										sourcePixArrayFor1D[check.b][check.a].label.b = boundaryLabel;
										System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
										curr = new Pair(check.a,check.b);
										if(dir%2==0){
											dir = (dir+7+inc)%8;
										}
										else
											dir = (dir+6+inc)%8;
										
										//tracingCount++;
										System.out.println("coord" + check.a + " " + check.b);
										
										biFiltered.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
										signal1DColor = boundaryColor;
										addPoint((pixCount*10)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[check.b][check.a].getIntensity()), signal1DColor, 3);
										break;
									}
								}
								
								inc++;
								//System.out.print("inc " + inc);
							}
							//System.out.println();
						}
						while(foundNext);
						
						//System.out.println("reached here?")	;//no
							
							
						boundaryLabel++;	
						System.out.println("new boundary label"+boundaryLabel);
					}
					
					
					redoCalcFlag = false;
				}
				
				/*				
				//place the shifted
				//offset1D = 0;
				for(int i =0; i<w; i++){
					addPoint(Tools.round(sourcePixArrayFor1D[mouseY][i].rx)-offset1D, Yorig+100-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Color.red, 4);
					if(i<w-1)
						addLine(Tools.round(sourcePixArrayFor1D[mouseY][i].rx)-offset1D, Yorig+100-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Tools.round(sourcePixArrayFor1D[mouseY][i+1].rx)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i+1].getIntensity()),Color.red);
				}
				
				//place the ones from original grid
				if(showOriginalGrid){
					for(int i =0; i<w; i++){
						addPoint(Tools.round(i*resizeScale)-offset1D, Yorig+100-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Color.black, 4);
						if(i<w-1)
							addLine(Tools.round(i*resizeScale)-offset1D, Yorig+100-Tools.round(sourcePixArrayFor1D[mouseY][i].getIntensity()), Tools.round(i*resizeScale+resizeScale)-offset1D, Yorig-Tools.round(sourcePixArrayFor1D[mouseY][i+1].getIntensity()),Color.black);
					}
				}*/
			}
			break;
			
		}
		case 5:{//boundary tracing visualization only show current and neighbors
			bi = ImageIO.read(new File(IMAGESOURCE));
			if(redoCalcFlag==true){
				int[][] intPixArray = convertTo2DUsingGetRGB(bi);
				sourcePixArrayFor1D = convert2DIntArrayToPixelArray(intPixArray);
				w = bi.getWidth(null);
				h = bi.getHeight(null);
				biFiltered = ImageIO.read(new File(IMAGESOURCE));
			}
			
			BufferedImage L0orig = ImageIO.read(new File(L0original));
			int[][] intPixArrayForOrig = convertTo2DUsingGetRGB(L0orig);
			sourcePixArrayForOrig = convert2DIntArrayToPixelArray(intPixArrayForOrig);
			
			
			int Xorig = 0;
			int Yorig = 800;
			
			if (mouseX >= w || mouseY >= h) {
				jtf.setText("mouse location exceed image boundaries");
			}
			else{
				
				
				if(redoCalcFlag==true){
					//start L0 calculation
					//find segment
					
					//reset label
					for(int i=1; i<h-1;i++){
						for(int j=1; j<w-1; j++){
							//sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
							//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
							sourcePixArrayFor1D[i][j].label = new Pair(-1,-1);
						}
					}
					
					//int maxPixelCount = w * h;
					//int currPixelCount = 0;
					int labelCount = 0;
					Pixel currPix = sourcePixArrayFor1D[mouseY][mouseX];
					ArrayList<Pixel> region = new ArrayList<Pixel>();
					ArrayList<Pair> tracing = new ArrayList<Pair>();
					ArrayList<Pair> outB = new ArrayList<Pair>();
					//ArrayList<Pair> inB = new ArrayList<Pair>();
					ArrayList<Pair> currB = new ArrayList<Pair>();
					ArrayList<Pair> foundB = new ArrayList<Pair>();
					boolean outBdone = false;
					//boolean inBdone = false;
					boolean mouseFound = false;
					
					region.clear();
					tracing.clear();
					region.add(currPix);
					Random r = new Random();
					Color tempColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					Color boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					//finding region
					while(!region.isEmpty()){
						Pixel temp = region.remove(0);
						tracing.add(new Pair(temp.getX(),temp.getY()));
						temp.label.a = labelCount;
						if(temp.getX()>0&&sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArrayFor1D[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()-1]);
						}
						if(temp.getX()<(w-1)&&sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()+1]);
						}
						if(temp.getY()>0&&sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()-1][temp.getX()]);
						}
						if(temp.getY()<(h-1)&&sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()+1][temp.getX()]);
						}
						//currPixelCount++;
						biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
					}
					
					//tracing
					//boundary tracing
					int boundaryLabel = 0;
					//int tracingCount = 0;
					while(true){
						//find the starting point -- top left
						Pair start = tracing.get(0);
						//System.out.print("default start a" + start.a + "b" + start.b);
						
						for(int i=0; i<tracing.size();i++){
							//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								start = tracing.get(i);
								break;
								//System.out.println("start changed?");
							}
						}
						
						if(sourcePixArrayFor1D[start.b][start.a].label.b!=-1){
							System.out.print("broke from can't find new start");
							break;
						}
						//System.out.print("not broke, start found");
						
						for(int i=0; i<tracing.size();i++){
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								if(tracing.get(i).b<start.b){
									start = tracing.get(i); System.out.print("start updated");
								}
								else if(tracing.get(i).b == start.b && tracing.get(i).a<start.a){
									start = tracing.get(i); System.out.print("start updated");
								}
									
								else{
									//System.out.print("start notupdated");
								}
							}
						}
						System.out.println("start at a"+start.a+"b"+start.b);
						
						
						
						//label start
						sourcePixArrayFor1D[start.b][start.a].label.b = boundaryLabel;
						System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[start.b][start.a].label.a +"b" + sourcePixArrayFor1D[start.b][start.a].label.b );
						//tracingCount++;
						System.out.println(" coord" + start.a + " " + start.b);
						Color signal1DColor = tempColor;
						boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
						biFiltered.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
						signal1DColor = boundaryColor;
						//addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[start.b][start.a].getIntensity()), signal1DColor, 3);
						currB.add(start);
						
						//tracingCount++;
						Pair curr = new Pair(start.a, start.b);
						int dir = 7;
						//int pixCount = 1;
						boolean foundNext = false;
						do{
							int inc = 0;
							
							//search next
							foundNext = false;
							while(inc<8){
								Pair check = new Pair(0,0);
								
								if(dir%2==0){
									check = L0.dirLookup(dir+7+inc);
								}
								else
									check = L0.dirLookup(dir+6+inc);
								
								check = new Pair(check.a+curr.a, check.b+curr.b);
								
								if(check.b>=0&&check.a>=0&&check.a<=w-1&&check.b<=h-1){
									if(check.a == start.a&&check.b == start.b){
										foundNext = false;
										break;
									}
									
									if((sourcePixArrayFor1D[check.b][check.a].label.b==-1||sourcePixArrayFor1D[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayFor1D[check.b][check.a].label.a==labelCount){
										//next found
										foundNext = true;
										//paint 1D signal
										//pixCount++;
										//add to container
										currB.add(check);
										if(check.a==mouseX&&check.b==mouseY){
											mouseFound = true;
										}
										sourcePixArrayFor1D[check.b][check.a].label.b = boundaryLabel;
										System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
										curr = new Pair(check.a,check.b);
										if(dir%2==0){
											dir = (dir+7+inc)%8;
										}
										else
											dir = (dir+6+inc)%8;
										
										//tracingCount++;
										System.out.println("coord" + check.a + " " + check.b);
										
										biFiltered.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
										signal1DColor = boundaryColor;
										//addPoint((pixCount*10)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[check.b][check.a].getIntensity()), signal1DColor, 3);
										break;
									}
								}
								
								inc++;
								//System.out.print("inc " + inc);
							}
							//System.out.println();
						}
						while(foundNext);
						
						//if found the mouse directed boundary
						if(mouseFound){
							//put current to mouse found container
							while(!currB.isEmpty()){
								foundB.add(currB.remove(0));
							}
							//no longer update out boundary container
							outBdone = true;
						}
						else{ //mouse not found, which is normal case
							if(!outBdone){
								outB.clear();
								while(!currB.isEmpty()){
									outB.add(currB.remove(0));
								}
							}
						}
						
							
						boundaryLabel++;	
						System.out.println("new boundary label"+boundaryLabel);
					}
					
					for(int i=0;i<outB.size();i++){
						addPoint((i*5)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[outB.get(i).b][outB.get(i).a].getIntensity()), Color.black, 3);
					}
					for(int i=0;i<foundB.size();i++){
						addPoint((i*5)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[foundB.get(i).b][foundB.get(i).a].getIntensity()), Color.white, 3);
					}
					
					redoCalcFlag = false;
				}
				
			}
			break;
			
		}
		
		case 6:{//boundary tracing visualization only show current and neighbors NORMALIZE
			bi = ImageIO.read(new File(IMAGESOURCE));
			if(redoCalcFlag==true){
				int[][] intPixArray = convertTo2DUsingGetRGB(bi);
				sourcePixArrayFor1D = convert2DIntArrayToPixelArray(intPixArray);
				w = bi.getWidth(null);
				h = bi.getHeight(null);
				biFiltered = ImageIO.read(new File(IMAGESOURCE));
			}
			
			BufferedImage L0orig = ImageIO.read(new File(L0original));
			int[][] intPixArrayForOrig = convertTo2DUsingGetRGB(L0orig);
			sourcePixArrayForOrig = convert2DIntArrayToPixelArray(intPixArrayForOrig);
			
			
			int Xorig = 0;
			int Yorig = 800;
			
			if (mouseX >= w || mouseY >= h) {
				jtf.setText("mouse location exceed image boundaries");
			}
			else{
				
				
				if(redoCalcFlag==true){
					//start L0 calculation
					//find segment
					
					//reset label
					for(int i=1; i<h-1;i++){
						for(int j=1; j<w-1; j++){
							//sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
							//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
							sourcePixArrayFor1D[i][j].label = new Pair(-1,-1);
						}
					}
					
					//int maxPixelCount = w * h;
					//int currPixelCount = 0;
					int labelCount = 0;
					Pixel currPix = sourcePixArrayFor1D[mouseY][mouseX];
					ArrayList<Pixel> region = new ArrayList<Pixel>();
					ArrayList<Pair> tracing = new ArrayList<Pair>();
					ArrayList<Pair> outB = new ArrayList<Pair>();
					//ArrayList<Pair> inB = new ArrayList<Pair>();
					ArrayList<Pair> currB = new ArrayList<Pair>();
					ArrayList<Pair> foundB = new ArrayList<Pair>();
					boolean outBdone = false;
					//boolean inBdone = false;
					boolean mouseFound = false;
					
					region.clear();
					tracing.clear();
					region.add(currPix);
					Random r = new Random();
					Color tempColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					Color boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					//finding region
					while(!region.isEmpty()){
						Pixel temp = region.remove(0);
						tracing.add(new Pair(temp.getX(),temp.getY()));
						temp.label.a = labelCount;
						if(temp.getX()>0&&sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArrayFor1D[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()-1]);
						}
						if(temp.getX()<(w-1)&&sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()+1]);
						}
						if(temp.getY()>0&&sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()-1][temp.getX()]);
						}
						if(temp.getY()<(h-1)&&sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()+1][temp.getX()]);
						}
						//currPixelCount++;
						biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
					}
					
					//tracing
					//boundary tracing
					int boundaryLabel = 0;
					//int tracingCount = 0;
					while(true){
						//find the starting point -- top left
						Pair start = tracing.get(0);
						//System.out.print("default start a" + start.a + "b" + start.b);
						
						for(int i=0; i<tracing.size();i++){
							//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								start = tracing.get(i);
								break;
								//System.out.println("start changed?");
							}
						}
						
						if(sourcePixArrayFor1D[start.b][start.a].label.b!=-1){
							System.out.print("broke from can't find new start");
							break;
						}
						//System.out.print("not broke, start found");
						
						for(int i=0; i<tracing.size();i++){
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								if(tracing.get(i).b<start.b){
									start = tracing.get(i); System.out.print("start updated");
								}
								else if(tracing.get(i).b == start.b && tracing.get(i).a<start.a){
									start = tracing.get(i); System.out.print("start updated");
								}
									
								else{
									//System.out.print("start notupdated");
								}
							}
						}
						System.out.println("start at a"+start.a+"b"+start.b);
						
						
						
						//label start
						sourcePixArrayFor1D[start.b][start.a].label.b = boundaryLabel;
						System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[start.b][start.a].label.a +"b" + sourcePixArrayFor1D[start.b][start.a].label.b );
						//tracingCount++;
						System.out.println(" coord" + start.a + " " + start.b);
						Color signal1DColor = tempColor;
						boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
						biFiltered.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
						signal1DColor = boundaryColor;
						//addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[start.b][start.a].getIntensity()), signal1DColor, 3);
						currB.add(start);
						
						//tracingCount++;
						Pair curr = new Pair(start.a, start.b);
						int dir = 7;
						//int pixCount = 1;
						boolean foundNext = false;
						do{
							int inc = 0;
							
							//search next
							foundNext = false;
							while(inc<8){
								Pair check = new Pair(0,0);
								
								if(dir%2==0){
									check = L0.dirLookup(dir+7+inc);
								}
								else
									check = L0.dirLookup(dir+6+inc);
								
								check = new Pair(check.a+curr.a, check.b+curr.b);
								
								if(check.b>=0&&check.a>=0&&check.a<=w-1&&check.b<=h-1){
									if(check.a == start.a&&check.b == start.b){
										foundNext = false;
										break;
									}
									
									if((sourcePixArrayFor1D[check.b][check.a].label.b==-1||sourcePixArrayFor1D[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayFor1D[check.b][check.a].label.a==labelCount){
										//next found
										foundNext = true;
										//paint 1D signal
										//pixCount++;
										//add to container
										currB.add(check);
										if(check.a==mouseX&&check.b==mouseY){
											mouseFound = true;
										}
										sourcePixArrayFor1D[check.b][check.a].label.b = boundaryLabel;
										System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
										curr = new Pair(check.a,check.b);
										if(dir%2==0){
											dir = (dir+7+inc)%8;
										}
										else
											dir = (dir+6+inc)%8;
										
										//tracingCount++;
										System.out.println("coord" + check.a + " " + check.b);
										
										biFiltered.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
										signal1DColor = boundaryColor;
										//addPoint((pixCount*10)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[check.b][check.a].getIntensity()), signal1DColor, 3);
										break;
									}
								}
								
								inc++;
								//System.out.print("inc " + inc);
							}
							//System.out.println();
						}
						while(foundNext);
						
						//if found the mouse directed boundary
						if(mouseFound){
							//put current to mouse found container
							while(!currB.isEmpty()){
								foundB.add(currB.remove(0));
							}
							//no longer update out boundary container
							outBdone = true;
						}
						else{ //mouse not found, which is normal case
							if(!outBdone){
								outB.clear();
								while(!currB.isEmpty()){
									outB.add(currB.remove(0));
								}
							}
						}
						
							
						boundaryLabel++;	
						System.out.println("new boundary label"+boundaryLabel);
					}
					
					if(!outB.isEmpty()){
						for(int i=0;i<200;i++){
							int index = (int)(outB.size()/200.0*i);
							addPoint((i*5)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[outB.get(index).b][outB.get(index).a].getIntensity()), Color.black, 3);
						}
					}
					
					for(int i=0;i<200;i++){
						int index = (int)(foundB.size()/200.0*i);
						addPoint((i*5)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[foundB.get(index).b][foundB.get(index).a].getIntensity()), Color.white, 3);
					}
					
					redoCalcFlag = false;
				}
				
			}
			break;
			
		}
		
		case 7:{//region merging
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					//sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int maxPixelCount = w * h;
			int currPixelCount = 0;
			int labelCount = 0;
			Pixel currPix = sourcePixArray[0][0];
			ArrayList<Pixel> currRegion = new ArrayList<Pixel>();
			//ArrayList<Pair> tracing = new ArrayList<Pair>();
			ArrayList<Region> regionCollection = new ArrayList<Region>();
			
			while(currPixelCount<maxPixelCount){
				//find the first unmarked pixel
				for(int i=0; i<maxPixelCount; i++){
					if(sourcePixArray[i/w][i%w].label.a<0){
						currPix = sourcePixArray[i/w][i%w];
						break;
					}
				}
				
				currRegion.clear();
				//tracing.clear();
				currRegion.add(currPix);
				Random r = new Random();
				Color tempColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
				//Color boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
				//finding region
				Region tempRegion = new Region(labelCount);
				
				while(!currRegion.isEmpty()){
					Pixel temp = currRegion.remove(0);
					//tracing.add(new Pair(temp.getX(),temp.getY()));
					temp.label.a = labelCount;
					tempRegion.pair.add(new Pair(temp.getX(),temp.getY()));
					tempRegion.size++;
					
					if(temp.getX()>0&&sourcePixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
						sourcePixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
						currRegion.add(sourcePixArray[temp.getY()][temp.getX()-1]);
					}
					if(temp.getX()<(w-1)&&sourcePixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
						sourcePixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
						currRegion.add(sourcePixArray[temp.getY()][temp.getX()+1]);
					}
					if(temp.getY()>0&&sourcePixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
						sourcePixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
						currRegion.add(sourcePixArray[temp.getY()-1][temp.getX()]);
					}
					if(temp.getY()<(h-1)&&sourcePixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
						sourcePixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
						currRegion.add(sourcePixArray[temp.getY()+1][temp.getX()]);
					}
					currPixelCount++;
					//biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
				}
				regionCollection.add(tempRegion);
				
				if(labelCount%100==0)
					System.out.println("label "+labelCount + " painted.");
				labelCount++;
				
				///if(labelCount>=2)
				//	break;
			}
			
			//===========Merging processes==================
			
			//populating priority queue
			PriorityQueue<Region> mergePQ = new PriorityQueue<Region>();
			for(int s=0;s<regionCollection.size();s++){
				if(regionCollection.get(s).size<=MERGESIZE){
					//find out how many neighbouring pixels are adjacent to big regions
					for(int i=0;i<regionCollection.get(s).size;i++){
						Pair tempP = regionCollection.get(s).pair.get(i);
						if(tempP.a>1&&regionCollection.get(sourcePixArray[tempP.b][tempP.a-1].label.a).size>MERGESIZE)
							regionCollection.get(s).numNeighborPixelsBelongToBigRegion++;
						if(tempP.a<w-1&&regionCollection.get(sourcePixArray[tempP.b][tempP.a+1].label.a).size>MERGESIZE)
							regionCollection.get(s).numNeighborPixelsBelongToBigRegion++;
						if(tempP.b<h-1&&regionCollection.get(sourcePixArray[tempP.b+1][tempP.a].label.a).size>MERGESIZE)
							regionCollection.get(s).numNeighborPixelsBelongToBigRegion++;
						if(tempP.b>1&&regionCollection.get(sourcePixArray[tempP.b-1][tempP.a].label.a).size>MERGESIZE)
							regionCollection.get(s).numNeighborPixelsBelongToBigRegion++;
					}
					mergePQ.add(regionCollection.get(s));
				}
			}
			
			//merge
			while(!mergePQ.isEmpty()){
				Region tempR = mergePQ.poll();
				//find the largetst neighbouring region
				int max = 0; 
				int maxRegionLabel = -1;
				for(int i=0;i<tempR.size;i++){
					Pair tempP = tempR.pair.get(i);
					if(tempP.a>1&&regionCollection.get(sourcePixArray[tempP.b][tempP.a-1].label.a).size>MERGESIZE){
						if(regionCollection.get(sourcePixArray[tempP.b][tempP.a-1].label.a).size>max){
							max = regionCollection.get(sourcePixArray[tempP.b][tempP.a-1].label.a).size;
							maxRegionLabel = sourcePixArray[tempP.b][tempP.a-1].label.a;
						}
					}
					if(tempP.a<w-1&&regionCollection.get(sourcePixArray[tempP.b][tempP.a+1].label.a).size>MERGESIZE){
						if(regionCollection.get(sourcePixArray[tempP.b][tempP.a+1].label.a).size>max){
							max = regionCollection.get(sourcePixArray[tempP.b][tempP.a+1].label.a).size;
							maxRegionLabel = sourcePixArray[tempP.b][tempP.a+1].label.a;
						}
					}
					if(tempP.b<h-1&&regionCollection.get(sourcePixArray[tempP.b+1][tempP.a].label.a).size>MERGESIZE){
						if(regionCollection.get(sourcePixArray[tempP.b+1][tempP.a].label.a).size>max){
							max = regionCollection.get(sourcePixArray[tempP.b+1][tempP.a].label.a).size;
							maxRegionLabel = sourcePixArray[tempP.b+1][tempP.a].label.a;
						}
					}
					if(tempP.b>1&&regionCollection.get(sourcePixArray[tempP.b-1][tempP.a].label.a).size>MERGESIZE){
						if(regionCollection.get(sourcePixArray[tempP.b-1][tempP.a].label.a).size>max){
							max = regionCollection.get(sourcePixArray[tempP.b-1][tempP.a].label.a).size;
							maxRegionLabel = sourcePixArray[tempP.b-1][tempP.a].label.a;
						}
					}
				}//largetst found
				
				//move these pixels to the large regions
				regionCollection.get(tempR.label).size=0;
				while(!regionCollection.get(tempR.label).pair.isEmpty()){
					Pair tempP = regionCollection.get(tempR.label).pair.remove(0);
					regionCollection.get(maxRegionLabel).size++;
					regionCollection.get(maxRegionLabel).pair.add(new Pair(tempP.a,tempP.b));
					sourcePixArray[tempP.b][tempP.a].label.a = maxRegionLabel;
				}
				
			}
			
			
			break;
		}
		case 8:{//boundary tracing on upsampled image //boundary tracing visualization only show current and neighbors NORMALIZE
			bi = ImageIO.read(new File(IMAGESOURCE));
			//Pixel[][] sourcePixArrayUpsampled;
			if(redoCalcFlag==true){
				int[][] intPixArray = convertTo2DUsingGetRGB(bi);
				sourcePixArrayFor1D = convert2DIntArrayToPixelArray(intPixArray);
				w = bi.getWidth(null);
				h = bi.getHeight(null);
				biFiltered = ImageIO.read(new File(IMAGESOURCE));
				biOrig = ImageIO.read(new File(IMAGESOURCE));
				biFiltered = blockyUpsampling(bi);
				int[][] intPixArrayUpsampled = convertTo2DUsingGetRGB(biFiltered);
				sourcePixArrayUpsampled = convert2DIntArrayToPixelArray(intPixArrayUpsampled);
			}
			
			
			
			BufferedImage L0orig = ImageIO.read(new File(L0original));
			int[][] intPixArrayForOrig = convertTo2DUsingGetRGB(L0orig);
			sourcePixArrayForOrig = convert2DIntArrayToPixelArray(intPixArrayForOrig);
			
			
			int Xorig = 0;
			int Yorig = 800;
			
			if (mouseX >= w || mouseY >= h) {
				jtf.setText("mouse location exceed image boundaries");
			}
			else{
				
				
				if(redoCalcFlag==true){
					//start L0 calculation
					//find segment
					
					//reset label
					for(int i=1; i<h-1;i++){
						for(int j=1; j<w-1; j++){
							//sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
							//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
							sourcePixArrayFor1D[i][j].label = new Pair(-1,-1);
						}
					}
					
					//int maxPixelCount = w * h;
					//int currPixelCount = 0;
					int labelCount = 0;
					Pixel currPix = sourcePixArrayFor1D[mouseY][mouseX];
					ArrayList<Pixel> region = new ArrayList<Pixel>();
					ArrayList<Pair> tracing = new ArrayList<Pair>();
					ArrayList<Pair> outB = new ArrayList<Pair>();
					//ArrayList<Pair> inB = new ArrayList<Pair>();
					ArrayList<Pair> currB = new ArrayList<Pair>();
					ArrayList<Pair> foundB = new ArrayList<Pair>();
					boolean outBdone = false;
					//boolean inBdone = false;
					boolean mouseFound = false;
					
					region.clear();
					tracing.clear();
					region.add(currPix);
					Random r = new Random();
					Color tempColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					Color boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
					//finding region
					while(!region.isEmpty()){
						Pixel temp = region.remove(0);
						tracing.add(new Pair(temp.getX(),temp.getY()));
						temp.label.a = labelCount;
						if(temp.getX()>0&&sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArrayFor1D[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()-1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()-1]);
						}
						if(temp.getX()<(w-1)&&sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()][temp.getX()+1].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()][temp.getX()+1]);
						}
						if(temp.getY()>0&&sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()-1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()-1][temp.getX()]);
						}
						if(temp.getY()<(h-1)&&sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArrayFor1D[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
							sourcePixArrayFor1D[temp.getY()+1][temp.getX()].label.a = labelCount;
							region.add(sourcePixArrayFor1D[temp.getY()+1][temp.getX()]);
						}
						//currPixelCount++;
						biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
					}
					
					//tracing on original image
					//boundary tracing
					int boundaryLabel = 0;
					//int tracingCount = 0;
					while(true){
						//find the starting point -- top left
						Pair start = tracing.get(0);
						//System.out.print("default start a" + start.a + "b" + start.b);
						
						for(int i=0; i<tracing.size();i++){
							//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								start = tracing.get(i);
								break;
								//System.out.println("start changed?");
							}
						}
						
						if(sourcePixArrayFor1D[start.b][start.a].label.b!=-1){
							System.out.print("broke from can't find new start");
							break;
						}
						//System.out.print("not broke, start found");
						
						for(int i=0; i<tracing.size();i++){
							if(sourcePixArrayFor1D[tracing.get(i).b][tracing.get(i).a].label.b==-1){
								if(tracing.get(i).b<start.b){
									start = tracing.get(i); System.out.print("start updated");
								}
								else if(tracing.get(i).b == start.b && tracing.get(i).a<start.a){
									start = tracing.get(i); System.out.print("start updated");
								}
									
								else{
									//System.out.print("start notupdated");
								}
							}
						}
						System.out.println("start at a"+start.a+"b"+start.b);
						
						
						
						//label start
						sourcePixArrayFor1D[start.b][start.a].label.b = boundaryLabel;
						System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[start.b][start.a].label.a +"b" + sourcePixArrayFor1D[start.b][start.a].label.b );
						//tracingCount++;
						System.out.println(" coord" + start.a + " " + start.b);
						Color signal1DColor = tempColor;
						boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
						biFiltered.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
						signal1DColor = boundaryColor;
						//addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[start.b][start.a].getIntensity()), signal1DColor, 3);
						currB.add(start);
						
						//tracingCount++;
						Pair curr = new Pair(start.a, start.b);
						int dir = 7;
						//int pixCount = 1;
						boolean foundNext = false;
						do{
							int inc = 0;
							
							//search next
							foundNext = false;
							while(inc<8){
								Pair check = new Pair(0,0);
								
								if(dir%2==0){
									check = L0.dirLookup(dir+7+inc);
								}
								else
									check = L0.dirLookup(dir+6+inc);
								
								check = new Pair(check.a+curr.a, check.b+curr.b);
								
								if(check.b>=0&&check.a>=0&&check.a<=w-1&&check.b<=h-1){
									if(check.a == start.a&&check.b == start.b){
										foundNext = false;
										break;
									}
									
									if((sourcePixArrayFor1D[check.b][check.a].label.b==-1||sourcePixArrayFor1D[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayFor1D[check.b][check.a].label.a==labelCount){
										//next found
										foundNext = true;
										//paint 1D signal
										//pixCount++;
										//add to container
										currB.add(check);
										if(check.a==mouseX&&check.b==mouseY){
											mouseFound = true;
										}
										sourcePixArrayFor1D[check.b][check.a].label.b = boundaryLabel;
										System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
										curr = new Pair(check.a,check.b);
										if(dir%2==0){
											dir = (dir+7+inc)%8;
										}
										else
											dir = (dir+6+inc)%8;
										
										//tracingCount++;
										System.out.println("coord" + check.a + " " + check.b);
										
										biOrig.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
										signal1DColor = boundaryColor;
										//addPoint((pixCount*10)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[check.b][check.a].getIntensity()), signal1DColor, 3);
										break;
									}
								}
								
								inc++;
								//System.out.print("inc " + inc);
							}
							//System.out.println();
						}
						while(foundNext);
						
						
						
						
						
						//if found the mouse directed boundary
						if(mouseFound){
							//put current to mouse found container
							while(!currB.isEmpty()){
								foundB.add(currB.remove(0));
							}
							//no longer update out boundary container
							outBdone = true;
						}
						else{ //mouse not found, which is normal case
							if(!outBdone){
								outB.clear();
								while(!currB.isEmpty()){
									outB.add(currB.remove(0));
								}
							}
						}
						
							
						boundaryLabel++;	
						System.out.println("new boundary label "+boundaryLabel);
					}
					
					
					//tracing on upsampled image
					System.out.println("Start on upsampled image");
					ArrayList<Pair> upsampTracing = new ArrayList<Pair>();
					//populating upsampleTracing array
					for(int i=0; i<foundB.size();i++){
						Pair tempP = foundB.get(i);
						for(int m=0;m<resizeScale;m++){
							for(int n=0; n<resizeScale;n++){
								upsampTracing.add(new Pair(tempP.a*resizeScale+m, tempP.b*resizeScale+n));
								sourcePixArrayUpsampled[tempP.b*resizeScale+n][tempP.a*resizeScale+m].label.a=labelCount;
							}
						}
					}
					
					Pair start = new Pair(foundB.get(0).a*resizeScale, foundB.get(0).b*resizeScale);
					System.out.println("start at a"+start.a + "b" + start.b);
					Pair curr = new Pair(start.a,start.b);
					int dir = 7;
					boolean foundNext = false;
					do{
						int inc = 0;
						//search next
						foundNext = false;
						while(inc<8){
							Pair check = new Pair(0,0);
							
							if(dir%2==0){
								check = L0.dirLookup(dir+7+inc);
							}
							else
								check = L0.dirLookup(dir+6+inc);
							
							check = new Pair(check.a+curr.a, check.b+curr.b);
							
							if(check.b>=0&&check.a>=0&&check.a<=biFiltered.getWidth()-1&&check.b<=biFiltered.getHeight()-1){
								if(check.a == start.a&&check.b == start.b){
									foundNext = false;
									break;
								}
								
								if((sourcePixArrayUpsampled[check.b][check.a].label.b==-1||sourcePixArrayUpsampled[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayUpsampled[check.b][check.a].label.a==labelCount){
									//next found
									foundNext = true;
									
									sourcePixArrayUpsampled[check.b][check.a].label.b = boundaryLabel;
									//System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
									curr = new Pair(check.a,check.b);
									if(dir%2==0){
										dir = (dir+7+inc)%8;
									}
									else
										dir = (dir+6+inc)%8;
									
									//tracingCount++;
									//System.out.println("coord" + check.a + " " + check.b);
									
									biFiltered.setRGB(check.a, check.b, Tools.getIntFromColor(Color.red));
									//signal1DColor = boundaryColor;
									//addPoint((pixCount*10)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[check.b][check.a].getIntensity()), signal1DColor, 3);
									break;
								}
							}
							
							inc++;
							//System.out.print("inc " + inc);
						}
						//System.out.println();
					}
					while(foundNext);
					
					/*
					if(!outB.isEmpty()){
						for(int i=0;i<200;i++){
							int index = (int)(outB.size()/200.0*i);
							addPoint((i*5)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[outB.get(index).b][outB.get(index).a].getIntensity()), Color.black, 3);
						}
					}
					
					for(int i=0;i<200;i++){
						int index = (int)(foundB.size()/200.0*i);
						addPoint((i*5)-offset1D, Yorig-Tools.round(sourcePixArrayForOrig[foundB.get(index).b][foundB.get(index).a].getIntensity()), Color.white, 3);
					}
					*/
					redoCalcFlag = false;
				}
				
			}
			break;
			
		}
		}
	}
	
	static BufferedImage blockyUpsampling(BufferedImage bi) {
		BufferedImage result = new BufferedImage(bi.getWidth()*resizeScale, bi.getHeight()*resizeScale, bi.getType());
		for(int i=0; i<result.getHeight();i++){
			for(int j=0; j<result.getWidth();j++){
				result.setRGB(j, i, 
						bi.getRGB(j/resizeScale, i/resizeScale));
			}
		}

		return result;
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
		f.getContentPane().setBackground(Color.gray);
		
		// p1 = new JPanel();
		final L0_visualization si = new L0_visualization();
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
		//panel.add(new JLabel("MaskSize"));
		//panel.add(jtfMaskSize);
		//panel.add(new JLabel("Scale"));
		//panel.add(jtfScale);
		//panel.add(new JLabel("Gamma"));
		//panel.add(jtfGamma);
		//panel.add(new JLabel("Alpha"));
		//panel.add(jtfAlpha);
		//panel.add(update);
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
				resizeScale = Integer.parseInt(jtfScale.getText());
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
	            else if(e.getKeyCode() == KeyEvent.VK_LEFT){
	            	mouseX--;
	            	redoCalcFlag = true;
	            	//points.clear();
	            	//lines.clear();
	            }
	            else if(e.getKeyCode() == KeyEvent.VK_RIGHT){
	            	mouseX++;
	            	redoCalcFlag = true;
	            	//points.clear();
	            	//lines.clear();
	            }
	            jtf.setText("" + mouseX + ", " + mouseY);
				MoG=null;
				points.clear();
				lines.clear();
				redoCalcFlag = true;
				si.repaint();
	         }        
	      });

	}
}