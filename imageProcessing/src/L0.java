

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

public class L0 extends Component implements ActionListener {

	String descs[] = { "Original", "bicubic upsampled", "fast Region", "b tracing on orig", "tracing on upsampled", "sobel", "random colored region","gapping on upsampled","two maps overlay"};

	static String IMAGESOURCE = "l0results/hood_grad_L0smoothed.png";
	//static String IMAGESOURCE = "C:/Users/Jay Li/Desktop/L0smoothing/code/7.jpg";
	//static String IMAGESOURCE = "C:/Users/Jay Li/Documents/aaresults/20150609/oldman_grad_L0smoothed.png";
	//static String IMAGESOURCE = "l0results/hood_1.1.png";
	//static String IMAGESOURCE = "testImgL0/L0dragonfly.png";
	//static String IMAGESOURCE = "landscape_ng.jpg";
	//static String IMAGESOURCE = "testImg141219/fattal/1.png";
	//static String IMAGESOURCE = "lines_thick.jpg";
	
	static String ORIGINALIMAGE = "l0results/hood_1.1.png"; //used for cross-filtering with original image

	//final int maskSize = 200;
	
	static double maskSize = 100;
	static int regionWH = (int) Math.sqrt(maskSize*16); //width or height of the sample region
	//final int upsampleMaskSize = 10;
	final double R = 1; //for geodesic
	static double gamma = 0.1; // for bilateral, for weight on intensity diff
	static int resizeScale = 4;
	//final int randomRange = 15;
	//final double exponentialPower = 7;
	//final int numOfPotEntrance = 10;
	//final int incrementalWeightInPot = 0;
	//final int potEntranceDensity = 25; //higher - less dense
	//final Color potPaintColor = Color.BLACK;
	
	final static LinkedList<Line> lines = new LinkedList<Line>(); //for histogram analysis
	final static LinkedList<Point> points = new LinkedList<Point>(); //for scatterplot analysis
	//static int histogram[] = new int[256];
	//static int scatterPlot[][] = new int[256][360];
	//static int logKernel[][];
	//static double MoG[][];
	//static boolean MoGisOn = false;
	//static int avgMog = 0;
	static double alpha = 0.5;
	//final static Color histogramColor = Color.RED;
	//final int histogramStartX = 1325;
	//final int histogramStartY = 80;
	//final int MASKBACKGROUND = 65280; // red = 16711680  green = 65280 blue = 255 Grey = 3289650
	
	//final double modeM = 0.5;
	//final int modeK = 5;
	
	//final double textureIntensity = 0.2; //define how much texture is added
	//final double texturenessStrength = 0.004; 
	//final double LLDDdelta = 0.99;
	//private static double avgTexturenessMap[][];
	
	//for mass-spring
	//final static double springK = 100;
	//final static double damperC = 0.5;
	//final static double angularSpringK = 1;
	
	//================================
	//final int SLICTIMESTEPS = 15;
	//final int TIMESTEPS = 50;
	//=================================
	//final static int SLIC_K = 200;
	//=================================
	//final static double SIGD = 1;
	
	//static Pixel[] clusterCenterArray = new Pixel[SLIC_K];
	
	//static boolean circleIsOn = false;
	//static boolean showOriginalGrid = true;
	//static boolean redoCalcFlag = false;
	//static int offset1D = 0;
	//static Pixel[][] sourcePixArray;
	//static double smoothingK = 1;
	//static double GradientK = 0.1;
	
	//========labeling==========
	final static double LABELTHRESHOLD = 1;
	final static int OVERLAYTHRESHOLD = 20;
	//==========================

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

	public L0() {
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
		
		case 2: {//fast Region
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int labelCount = 0;
			Pixel currPix = sourcePixArray[0][0];
			ArrayList<Pixel> region = new ArrayList<Pixel>();
			ArrayList<Pair> tracing = new ArrayList<Pair>();
			
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].label.a<0){
						//find region
						currPix = sourcePixArray[i][j];
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
							if(temp.getX()>0&&sourcePixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()-1]);
							}
							if(temp.getX()<(w-1)&&sourcePixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()+1]);
							}
							if(temp.getY()>0&&sourcePixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()-1][temp.getX()]);
							}
							if(temp.getY()<(h-1)&&sourcePixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()+1][temp.getX()]);
							}
							//biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
						}
						labelCount++;
						int size = tracing.size();
						int threshold = 6;
						int color = Tools.getIntFromColor(Color.white );
						if(size>threshold)
							color = Tools.getIntFromColor(Color.black );
						while(!tracing.isEmpty()){
							Pair temp = tracing.remove(0);
							biFiltered.setRGB(temp.a, temp.b, color);
						}
						//System.out.println("tracing size" + tracing.size());
						/*for(int k =0;k<tracing.size();k++){
							System.out.println("a"+tracing.get(k).a+"b"+tracing.get(k).b);
						}*/
					}
					
				}
				
			}
			
			System.out.println("label count:" + labelCount);
			
			break;
		}
		
		case 3:{//tracing on orig
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int labelCount = 0;
			Pixel currPix = sourcePixArray[0][0];
			ArrayList<Pixel> region = new ArrayList<Pixel>();
			ArrayList<Pair> tracing = new ArrayList<Pair>();
			
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].label.a<0){
						//find region
						currPix = sourcePixArray[i][j];
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
							if(temp.getX()>0&&sourcePixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()-1]);
							}
							if(temp.getX()<(w-1)&&sourcePixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()+1]);
							}
							if(temp.getY()>0&&sourcePixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()-1][temp.getX()]);
							}
							if(temp.getY()<(h-1)&&sourcePixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()+1][temp.getX()]);
							}
							biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
						}
						
						//System.out.println("tracing size" + tracing.size());
						
						
						//tracing
						//boundary tracing
						int boundaryLabel = 0;
						//int tracingCount = 0;
						while(true){
							//find the starting point -- top left
							Pair start = tracing.get(0);
							//System.out.print("default start a" + start.a + "b" + start.b);
							
							for(int m=0; m<tracing.size();m++){
								//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
								if(sourcePixArray[tracing.get(m).b][tracing.get(m).a].label.b==-1){
									start = tracing.get(m);
									break;
									//System.out.println("start changed?");
								}
							}
							
							if(sourcePixArray[start.b][start.a].label.b!=-1){
								//System.out.print("broke from can't find new start");
								break;
							}
							//System.out.print("not broke, start found");
							
							for(int m=0; m<tracing.size();m++){
								if(sourcePixArray[tracing.get(m).b][tracing.get(m).a].label.b==-1){
									if(tracing.get(m).b<start.b){
										start = tracing.get(m); //System.out.print("start updated");
									}
									else if(tracing.get(m).b == start.b && tracing.get(m).a<start.a){
										start = tracing.get(m); //System.out.print("start updated");
									}
										
									else{
										//System.out.print("start notupdated");
									}
								}
							}
							//System.out.println("start at a"+start.a+"b"+start.b);
							
							//label start
							sourcePixArray[start.b][start.a].label.b = boundaryLabel;
							//System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArray[start.b][start.a].label.a +"b" + sourcePixArray[start.b][start.a].label.b );
							//tracingCount++;
							//System.out.println(" coord" + start.a + " " + start.b);
							Color signal1DColor = tempColor;
							boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
							biFiltered.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
							signal1DColor = boundaryColor;
							//addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArray[start.b][start.a].getIntensity()), signal1DColor, 3);
							
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
											//System.out.println("broke from coming back to start");
											break;
										}
										
										if((sourcePixArray[check.b][check.a].label.b==-1||sourcePixArray[check.b][check.a].label.b==boundaryLabel)&&sourcePixArray[check.b][check.a].label.a==labelCount){
											//next found
											foundNext = true;
											//paint 1D signal
											pixCount++;
											sourcePixArray[check.b][check.a].label.b = boundaryLabel;
											//System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArray[check.b][check.a].label.a +"b" + sourcePixArray[check.b][check.a].label.b + " inc" + inc);
											curr = new Pair(check.a,check.b);
											if(dir%2==0){
												dir = (dir+7+inc)%8;
											}
											else
												dir = (dir+6+inc)%8;
											
											//tracingCount++;
											//System.out.println("coord" + check.a + " " + check.b);
											
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
							
							
							
							boundaryLabel++;	
							//System.out.println("new boundary label"+boundaryLabel);
						}//System.out.println("label count:" + labelCount);
						
						//after tracing
						labelCount++;
					}
					
				}
				System.out.println("done row" + i);
			}
			
			break;
		}
		
		case 4:{//tracing on upsampled
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			biFiltered = blockyUpsampling(bi);
			int[][] intPixArrayUpsampled = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArrayUpsampled = convert2DIntArrayToPixelArray(intPixArrayUpsampled);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int labelCount = 0;
			Pixel currPix = sourcePixArray[0][0];
			ArrayList<Pixel> region = new ArrayList<Pixel>();
			ArrayList<Pair> tracing = new ArrayList<Pair>();
			
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].label.a<0){
						//find region
						currPix = sourcePixArray[i][j];
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
							if(temp.getX()>0&&sourcePixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()-1]);
							}
							if(temp.getX()<(w-1)&&sourcePixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()+1]);
							}
							if(temp.getY()>0&&sourcePixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()-1][temp.getX()]);
							}
							if(temp.getY()<(h-1)&&sourcePixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()+1][temp.getX()]);
							}
							biOrig.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
						}
						
						//System.out.println("tracing size" + tracing.size());
						
						
						//tracing
						//boundary tracing
						int boundaryLabel = 0;
						//int tracingCount = 0;
						while(true){
							//find the starting point -- top left
							Pair start = tracing.get(0);
							//System.out.print("default start a" + start.a + "b" + start.b);
							
							for(int m=0; m<tracing.size();m++){
								//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
								if(sourcePixArray[tracing.get(m).b][tracing.get(m).a].label.b==-1){
									start = tracing.get(m);
									break;
									//System.out.println("start changed?");
								}
							}
							
							if(sourcePixArray[start.b][start.a].label.b!=-1){
								System.out.print("broke from can't find new start");
								break;
							}
							//System.out.print("not broke, start found");
							
							for(int m=0; m<tracing.size();m++){
								if(sourcePixArray[tracing.get(m).b][tracing.get(m).a].label.b==-1){
									if(tracing.get(m).b<start.b){
										start = tracing.get(m); //System.out.print("start updated");
									}
									else if(tracing.get(m).b == start.b && tracing.get(m).a<start.a){
										start = tracing.get(m); //System.out.print("start updated");
									}
										
									else{
										//System.out.print("start notupdated");
									}
								}
							}
							//System.out.println("start at a"+start.a+"b"+start.b);
							
							//label start
							ArrayList<Pair> currRing = new ArrayList<Pair>();
							currRing.add(new Pair(start.a,start.b));
							sourcePixArray[start.b][start.a].label.b = boundaryLabel;
							//System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArray[start.b][start.a].label.a +"b" + sourcePixArray[start.b][start.a].label.b );
							//tracingCount++;
							//System.out.println(" coord" + start.a + " " + start.b);
							//Color signal1DColor = tempColor;
							boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
							biOrig.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
							//signal1DColor = boundaryColor;
							//addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArray[start.b][start.a].getIntensity()), signal1DColor, 3);
							
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
											//System.out.println("broke from coming back to start");
											break;
										}
										
										if((sourcePixArray[check.b][check.a].label.b==-1||sourcePixArray[check.b][check.a].label.b==boundaryLabel)&&sourcePixArray[check.b][check.a].label.a==labelCount){
											//next found
											foundNext = true;
											//paint 1D signal
											//pixCount++;
											sourcePixArray[check.b][check.a].label.b = boundaryLabel;
											//System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArray[check.b][check.a].label.a +"b" + sourcePixArray[check.b][check.a].label.b + " inc" + inc);
											curr = new Pair(check.a,check.b);
											if(dir%2==0){
												dir = (dir+7+inc)%8;
											}
											else
												dir = (dir+6+inc)%8;
											
											//tracingCount++;
											//System.out.println("coord" + check.a + " " + check.b);
											
											biOrig.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
											currRing.add(new Pair(check.a,check.b));
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
							
							
							//tracing on upsampled image
							System.out.println("Start on upsampled image a"+labelCount + "b"+boundaryLabel +"currRingSize"+currRing.size());
							ArrayList<Pair> upsampTracing = new ArrayList<Pair>();
							//populating upsampleTracing array
							Color ringBg = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
							for(int k=0; k<currRing.size();k++){
								Pair tempP = currRing.get(k);
								for(int m=0;m<resizeScale;m++){
									for(int n=0; n<resizeScale;n++){
										upsampTracing.add(new Pair(tempP.a*resizeScale+m, tempP.b*resizeScale+n));
										sourcePixArrayUpsampled[tempP.b*resizeScale+n][tempP.a*resizeScale+m].label.a=labelCount+boundaryLabel;
										//biFiltered.setRGB(tempP.a*resizeScale+m, tempP.b*resizeScale+n, Tools.getIntFromColor(ringBg));
									}
								}
							}
							
							Pair u_start = new Pair(currRing.get(0).a*resizeScale, currRing.get(0).b*resizeScale);
							biFiltered.setRGB(u_start.a, u_start.b, Tools.getIntFromColor(boundaryColor));
							//System.out.println("start at a"+start.a + "b" + start.b);
							Pair u_curr = new Pair(u_start.a,u_start.b);
							dir = 7;
							boolean u_foundNext = false;
							do{
								int inc = 0;
								//search next
								u_foundNext = false;
								while(inc<8){
									Pair check = new Pair(0,0);
									
									if(dir%2==0){
										check = L0.dirLookup(dir+7+inc);
									}
									else
										check = L0.dirLookup(dir+6+inc);
									
									check = new Pair(check.a+u_curr.a, check.b+u_curr.b);
									
									if(check.b>=0&&check.a>=0&&check.a<=biFiltered.getWidth()-1&&check.b<=biFiltered.getHeight()-1){
										if(check.a == u_start.a&&check.b == u_start.b){
											u_foundNext = false;
											break;
										}
										
										if((sourcePixArrayUpsampled[check.b][check.a].label.b==-1||sourcePixArrayUpsampled[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayUpsampled[check.b][check.a].label.a==labelCount+boundaryLabel){
											//next found
											u_foundNext = true;
											
											sourcePixArrayUpsampled[check.b][check.a].label.b = boundaryLabel;
											//System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
											u_curr = new Pair(check.a,check.b);
											if(dir%2==0){
												dir = (dir+7+inc)%8;
											}
											else
												dir = (dir+6+inc)%8;
											
											//tracingCount++;
											//System.out.println("coord" + check.a + " " + check.b);
											
											biFiltered.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
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
							while(u_foundNext);
							
							boundaryLabel++;	
							//System.out.println("new boundary label"+boundaryLabel);
						}//System.out.println("label count:" + labelCount);
						
						//after tracing
						labelCount++;
					}
				}
			
				//System.out.println("done row" + i);
			}
			
			break;
		}
		
		case 5: {//sobel
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					//sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			double [][] Gx = new double[w][h];   
			double [][] Gy = new double[w][h];   
			double [][] G  = new double[w][h];   
		   
		    for (int i=0; i<w; i++) {   
		      for (int j=0; j<h; j++) {   
		        if (i==0 || i==w-1 || j==0 || j==h-1)   
		          Gx[i][j] = Gy[i][j] = G[i][j] = 0; // Image boundary cleared   
		        else{   
		          Gx[i][j] = 
		        		  sourcePixArray[j-1][i+1].intensity + 2*sourcePixArray[j][i+1].intensity + sourcePixArray[j+1][i+1].intensity  -   
		        		  sourcePixArray[j-1][i-1].intensity - 2*sourcePixArray[j][i-1].intensity - sourcePixArray[j+1][i-1].intensity;   
		          Gy[i][j] = sourcePixArray[j+1][i-1].intensity + 2*sourcePixArray[j+1][i].intensity + sourcePixArray[j+1][i+1].intensity -   
		        		  sourcePixArray[j-1][i-1].intensity - 2*sourcePixArray[j-1][i].intensity - sourcePixArray[j-1][i+1].intensity;   
		          G[i][j]  = Math.sqrt(Math.abs(Gx[i][j])*Math.abs(Gx[i][j]) + Math.abs(Gy[i][j])*Math.abs(Gy[i][j]));   
		        }
		        int col = (int) G[i][j];
		        if(col>255)
		        	col = 255;
		        Color c = new Color(col,col,col);
		        biFiltered.setRGB(i, j, Tools.getIntFromColor(c));
		      }   
		    }
		    break;
		}
		case 6:{//random colored region
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int labelCount = 0;
			Pixel currPix = sourcePixArray[0][0];
			ArrayList<Pixel> region = new ArrayList<Pixel>();
			ArrayList<Pair> tracing = new ArrayList<Pair>();
			
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].label.a<0){
						//find region
						currPix = sourcePixArray[i][j];
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
							if(temp.getX()>0&&sourcePixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()-1]);
							}
							if(temp.getX()<(w-1)&&sourcePixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()+1]);
							}
							if(temp.getY()>0&&sourcePixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()-1][temp.getX()]);
							}
							if(temp.getY()<(h-1)&&sourcePixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()+1][temp.getX()]);
							}
							biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
						}
						labelCount++;
						/*int size = tracing.size();
						int threshold = 3;
						int color = Tools.getIntFromColor(Color.white );
						if(size>threshold)
							color = Tools.getIntFromColor(Color.black );
						while(!tracing.isEmpty()){
							Pair temp = tracing.remove(0);
							biFiltered.setRGB(temp.a, temp.b, color);
						}*/
						//System.out.println("tracing size" + tracing.size());
						/*for(int k =0;k<tracing.size();k++){
							System.out.println("a"+tracing.get(k).a+"b"+tracing.get(k).b);
						}*/
					}
					
				}
				
			}
			
			System.out.println("label count:" + labelCount);
			
			break;
		}
		
		case 7:{//gapping on upsampled
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biOrig = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			biFiltered = blockyUpsampling(bi);
			int[][] intPixArrayUpsampled = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArrayUpsampled = convert2DIntArrayToPixelArray(intPixArrayUpsampled);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					sourcePixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int labelCount = 0;
			Pixel currPix = sourcePixArray[0][0];
			ArrayList<Pixel> region = new ArrayList<Pixel>();
			ArrayList<Pair> tracing = new ArrayList<Pair>();
			
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].label.a<0){
						//find region
						currPix = sourcePixArray[i][j];
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
							if(temp.getX()>0&&sourcePixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), sourcePixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()-1]);
							}
							if(temp.getX()<(w-1)&&sourcePixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()][temp.getX()+1]);
							}
							if(temp.getY()>0&&sourcePixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()-1][temp.getX()]);
							}
							if(temp.getY()<(h-1)&&sourcePixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , sourcePixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								sourcePixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
								region.add(sourcePixArray[temp.getY()+1][temp.getX()]);
							}
							biOrig.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
						}
						
						//System.out.println("tracing size" + tracing.size());
						
						
						//tracing
						//boundary tracing
						int boundaryLabel = 0;
						//int tracingCount = 0;
						while(true){
							//find the starting point -- top left
							Pair start = tracing.get(0);
							//System.out.print("default start a" + start.a + "b" + start.b);
							
							for(int m=0; m<tracing.size();m++){
								//System.out.print("tracing a" + tracing.get(i).a + "b"+tracing.get(i).b );
								if(sourcePixArray[tracing.get(m).b][tracing.get(m).a].label.b==-1){
									start = tracing.get(m);
									break;
									//System.out.println("start changed?");
								}
							}
							
							if(sourcePixArray[start.b][start.a].label.b!=-1){
								System.out.print("broke from can't find new start");
								break;
							}
							//System.out.print("not broke, start found");
							
							for(int m=0; m<tracing.size();m++){
								if(sourcePixArray[tracing.get(m).b][tracing.get(m).a].label.b==-1){
									if(tracing.get(m).b<start.b){
										start = tracing.get(m); //System.out.print("start updated");
									}
									else if(tracing.get(m).b == start.b && tracing.get(m).a<start.a){
										start = tracing.get(m); //System.out.print("start updated");
									}
										
									else{
										//System.out.print("start notupdated");
									}
								}
							}
							//System.out.println("start at a"+start.a+"b"+start.b);
							
							//label start
							ArrayList<Pair> currRing = new ArrayList<Pair>();
							currRing.add(new Pair(start.a,start.b));
							sourcePixArray[start.b][start.a].label.b = boundaryLabel;
							//System.out.print("start found bLabel" +boundaryLabel+"a"+ sourcePixArray[start.b][start.a].label.a +"b" + sourcePixArray[start.b][start.a].label.b );
							//tracingCount++;
							//System.out.println(" coord" + start.a + " " + start.b);
							//Color signal1DColor = tempColor;
							boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
							biOrig.setRGB(start.a, start.b, Tools.getIntFromColor(boundaryColor));
							//signal1DColor = boundaryColor;
							//addPoint((0)-offset1D, Yorig-Tools.round(sourcePixArray[start.b][start.a].getIntensity()), signal1DColor, 3);
							
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
											//System.out.println("broke from coming back to start");
											break;
										}
										
										if((sourcePixArray[check.b][check.a].label.b==-1||sourcePixArray[check.b][check.a].label.b==boundaryLabel)&&sourcePixArray[check.b][check.a].label.a==labelCount){
											//next found
											foundNext = true;
											//paint 1D signal
											//pixCount++;
											sourcePixArray[check.b][check.a].label.b = boundaryLabel;
											//System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArray[check.b][check.a].label.a +"b" + sourcePixArray[check.b][check.a].label.b + " inc" + inc);
											curr = new Pair(check.a,check.b);
											if(dir%2==0){
												dir = (dir+7+inc)%8;
											}
											else
												dir = (dir+6+inc)%8;
											
											//tracingCount++;
											//System.out.println("coord" + check.a + " " + check.b);
											
											biOrig.setRGB(check.a, check.b, Tools.getIntFromColor(boundaryColor));
											currRing.add(new Pair(check.a,check.b));
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
							
							
							//tracing on upsampled image
							System.out.println("Start on upsampled image a"+labelCount + "b"+boundaryLabel +"currRingSize"+currRing.size());
							ArrayList<Pair> upsampTracing = new ArrayList<Pair>();
							//populating upsampleTracing array
							Color ringBg = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
							for(int k=0; k<currRing.size();k++){
								Pair tempP = currRing.get(k);
								for(int m=0;m<resizeScale;m++){
									for(int n=0; n<resizeScale;n++){
										upsampTracing.add(new Pair(tempP.a*resizeScale+m, tempP.b*resizeScale+n));
										sourcePixArrayUpsampled[tempP.b*resizeScale+n][tempP.a*resizeScale+m].label.a=labelCount+boundaryLabel;
										//biFiltered.setRGB(tempP.a*resizeScale+m, tempP.b*resizeScale+n, Tools.getIntFromColor(ringBg));
									}
								}
							}
							
							Pair u_start = new Pair(currRing.get(0).a*resizeScale, currRing.get(0).b*resizeScale);
							biFiltered.setRGB(u_start.a, u_start.b, Tools.getIntFromColor(boundaryColor));
							//System.out.println("start at a"+start.a + "b" + start.b);
							Pair u_curr = new Pair(u_start.a,u_start.b);
							dir = 7;
							boolean u_foundNext = false;
							do{
								int inc = 0;
								//search next
								u_foundNext = false;
								while(inc<8){
									Pair check = new Pair(0,0);
									
									if(dir%2==0){
										check = L0.dirLookup(dir+7+inc);
									}
									else
										check = L0.dirLookup(dir+6+inc);
									
									check = new Pair(check.a+u_curr.a, check.b+u_curr.b);
									
									if(check.b>=0&&check.a>=0&&check.a<=biFiltered.getWidth()-1&&check.b<=biFiltered.getHeight()-1){
										if(check.a == u_start.a&&check.b == u_start.b){
											u_foundNext = false;
											break;
										}
										
										if((sourcePixArrayUpsampled[check.b][check.a].label.b==-1||sourcePixArrayUpsampled[check.b][check.a].label.b==boundaryLabel)&&sourcePixArrayUpsampled[check.b][check.a].label.a==labelCount+boundaryLabel){
											//next found
											u_foundNext = true;
											
											sourcePixArrayUpsampled[check.b][check.a].label.b = boundaryLabel;
											//System.out.print("next found bLabel" +boundaryLabel+"a"+ sourcePixArrayFor1D[check.b][check.a].label.a +"b" + sourcePixArrayFor1D[check.b][check.a].label.b + " inc" + inc);
											u_curr = new Pair(check.a,check.b);
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
							while(u_foundNext);
							
							boundaryLabel++;
							break;
							//System.out.println("new boundary label"+boundaryLabel);
						}//System.out.println("label count:" + labelCount);
						
						//after tracing
						labelCount++;
					}
				}
			
				//System.out.println("done row" + i);
			}
			
			break;
			
		}
		case 8:{//fast Region
			
			biFiltered = ImageIO.read(new File(ORIGINALIMAGE));
			int[][] origPixArray = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] origL0PixArray = convert2DIntArrayToPixelArray(origPixArray);
			
			bi = ImageIO.read(new File(IMAGESOURCE));
			int[][] gradintPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] gradPixArray = convert2DIntArrayToPixelArray(gradintPixArray);
			
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					origL0PixArray[i][j].intensity = origL0PixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					origL0PixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					gradPixArray[i][j].intensity = gradPixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
					gradPixArray[i][j].label = new Pair(-1,-1);
				}
			}
			
			int labelCount = 0;
			Pixel currPix = origL0PixArray[0][0];
			ArrayList<Pixel> region = new ArrayList<Pixel>();
			ArrayList<Pair> tracingOnOrig = new ArrayList<Pair>();
			ArrayList<Pair> tracingOnGrad = new ArrayList<Pair>();
			
			for(int i=0;i<h;i++){
				for(int j=0;j<w;j++){
						//find region on L0 smoothened original image
					int black = Tools.getIntFromColor(Color.black);
					int white = Tools.getIntFromColor(Color.white);
					if(origL0PixArray[i][j].label.a<0){
						currPix = origL0PixArray[i][j];
						region.clear();
						tracingOnOrig.clear();
						region.add(currPix);
						Random r = new Random();
						Color tempColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
						Color boundaryColor = new Color((int)(r.nextDouble()*255), (int)(r.nextDouble()*255), (int)(r.nextDouble()*255));
						//finding region
						while(!region.isEmpty()){
							Pixel temp = region.remove(0);
							tracingOnOrig.add(new Pair(temp.getX(),temp.getY()));
							temp.label.a = labelCount;
							if(temp.getX()>0&&origL0PixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), origL0PixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								origL0PixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
								region.add(origL0PixArray[temp.getY()][temp.getX()-1]);
							}
							if(temp.getX()<(w-1)&&origL0PixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , origL0PixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								origL0PixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
								region.add(origL0PixArray[temp.getY()][temp.getX()+1]);
							}
							if(temp.getY()>0&&origL0PixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , origL0PixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								origL0PixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
								region.add(origL0PixArray[temp.getY()-1][temp.getX()]);
							}
							if(temp.getY()<(h-1)&&origL0PixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , origL0PixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
								origL0PixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
								region.add(origL0PixArray[temp.getY()+1][temp.getX()]);
							}
							//biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
						}
						
						if(tracingOnOrig.size()>OVERLAYTHRESHOLD){
							while(!tracingOnOrig.isEmpty()){
								Pair temp = tracingOnOrig.remove(0);
								biFiltered.setRGB(temp.a, temp.b, black);
							}
						}
						else{//find region on the L0 smoothened gradient map
							while(!tracingOnOrig.isEmpty()){
								Pair temp = tracingOnOrig.remove(0);
								origL0PixArray[temp.b][temp.a].label.a =-1;
							}
							origL0PixArray[i][j].label.a = labelCount;
							
							currPix = gradPixArray[i][j];
							region.clear();
							tracingOnGrad.clear();
							region.add(currPix);
							//finding region
							while(!region.isEmpty()){
								Pixel temp = region.remove(0);
								tracingOnGrad.add(new Pair(temp.getX(),temp.getY()));
								temp.label.a = labelCount;
								if(temp.getX()>0&&gradPixArray[temp.getY()][temp.getX()-1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor(), gradPixArray[temp.getY()][temp.getX()-1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
									gradPixArray[temp.getY()][temp.getX()-1].label.a = labelCount;
									region.add(gradPixArray[temp.getY()][temp.getX()-1]);
								}
								if(temp.getX()<(w-1)&&gradPixArray[temp.getY()][temp.getX()+1].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , gradPixArray[temp.getY()][temp.getX()+1].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
									gradPixArray[temp.getY()][temp.getX()+1].label.a = labelCount;
									region.add(gradPixArray[temp.getY()][temp.getX()+1]);
								}
								if(temp.getY()>0&&gradPixArray[temp.getY()-1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , gradPixArray[temp.getY()-1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
									gradPixArray[temp.getY()-1][temp.getX()].label.a = labelCount;
									region.add(gradPixArray[temp.getY()-1][temp.getX()]);
								}
								if(temp.getY()<(h-1)&&gradPixArray[temp.getY()+1][temp.getX()].label.a<0&&(Tools.intensityDiffsqrd(currPix.getColor() , gradPixArray[temp.getY()+1][temp.getX()].getColor()))<LABELTHRESHOLD*LABELTHRESHOLD){
									gradPixArray[temp.getY()+1][temp.getX()].label.a = labelCount;
									region.add(gradPixArray[temp.getY()+1][temp.getX()]);
								}
								//biFiltered.setRGB(temp.getX(), temp.getY(), Tools.getIntFromColor(tempColor));
							}
							
							if(tracingOnGrad.size()<OVERLAYTHRESHOLD){
								biFiltered.setRGB(j, i, white);
								while(!tracingOnGrad.isEmpty()){
									Pair temp = tracingOnGrad.remove(0);
									gradPixArray[temp.b][temp.a].label.a = -1;
								}
							}
							else{
								while(!tracingOnGrad.isEmpty()){
									Pair temp = tracingOnGrad.remove(0);
									origL0PixArray[temp.b][temp.a].label.a = labelCount;
									biFiltered.setRGB(temp.a, temp.b, black);
								}
							}
							
						}
						

						labelCount++;
					}
				}
				System.out.println("i"+i);
				
			}
			
			System.out.println("label count:" + labelCount);
			
			break;
		}
		}
	}
	
	
	public static Pair dirLookup (int i){
		int j = i%8;
		if(j==1)
			return new Pair(1,-1);
		else if(j==2)
			return new Pair(0,-1);
		else if(j==3)
			return new Pair(-1,-1);
		else if(j==4)
			return new Pair(-1,0);
		else if(j==5)
			return new Pair(-1,1);
		else if(j==6)
			return new Pair(0,1);
		else if(j==7)
			return new Pair(1,1);
		else if(j==0)
			return new Pair(1,0);
		else
			return new Pair(0,0);
		
	}

	
	public static double angleBetweenTwoVector(Vec2 v1, Vec2 v2) {
		if(v1.getLength()==0||v2.getLength()==0)
			return 3.1415926*0.5;
		else
			return Math.acos((v1.x*v2.x + v1.y*v2.y)/(v1.getLength()*v2.getLength()));
	}

	

	
	static double rand(Vec2 co){
	    return fract(Math.sin(Vec2.dot(co , new Vec2(12.9898,78.233))) * 43758.5453);
	}
	
	static double fract(double a){
		return a-(int)a;
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
	
	static double turns (Pixel b, Pixel p, Pixel q){
		return (p.rx - b.rx)*(-q.ry + b.ry)-(-p.ry +b.ry)*(q.rx - b.rx);
	}
	
	static double triArea(Pixel t1, Pixel t2, Pixel t3){
		return Math.abs(0.5*(t1.rx*(t2.ry-t3.ry)+ t2.rx*(t3.ry-t1.ry)+ t3.rx*(t1.ry-t2.ry)));
	}
	
	static double cross(  Vec2 a,  Vec2 b ) { return a.x*b.y - a.y*b.x; }

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
		double i1 = Math.sqrt(c1.getRed()*c1.getRed()+c1.getBlue()*c1.getBlue()+c1.getGreen()*c1.getGreen())/443.405*256;
		double i2 = Math.sqrt(c2.getRed()*c2.getRed()+c2.getBlue()*c2.getBlue()+c2.getGreen()*c2.getGreen())/443.405*256;
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
	public static void testing(){
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
		final L0 si = new L0();
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
				//resizeScale = Double.parseDouble(jtfScale.getText());
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
				points.clear();
				lines.clear();
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
	            
	            jtf.setText("" + mouseX + ", " + mouseY);
	            points.clear();
				si.repaint();
	         }        
	      });

	}
}