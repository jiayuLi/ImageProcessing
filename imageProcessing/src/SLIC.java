

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

public class SLIC extends Component implements ActionListener {
	
	String descs[] = { "Original", "bicubic upsampled", "SLIC", "SLIC_NEW","1D spring mass", "Length distribution"};

	//static String IMAGESOURCE = "testImg141219/all/24.jpg";
	static String IMAGESOURCE = "texim/imgwarp.png";
	//static String IMAGESOURCE = "landscape_ng.jpg";
	//static String IMAGESOURCE = "testImg141219/fattal/1.png";
	//static String IMAGESOURCE = "lines_thick.jpg";
	
	static String ORIGINALIMAGE = "redpicnictable.jpg"; //used for cross-filtering with original image

	//final int maskSize = 200;
	
	static double maskSize = 100;
	static int regionWH = (int) Math.sqrt(maskSize*16); //width or height of the sample region
	//final int upsampleMaskSize = 10;
	final double R = 1; //for geodesic
	static double gamma = 0.1; // for bilateral, for weight on intensity diff
	static double resizeScale = 1;
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
	final static double springK = 100;
	final static double damperC = 0.5;
	final static double angularSpringK = 1;
	
	//================================
	final int SLICTIMESTEPS = 15;
	final int TIMESTEPS = 50;
	//=================================
	final static int SLIC_K = 400;
	//=================================
	final static double SIGD = 1;
	
	//static Pixel[] clusterCenterArray = new Pixel[SLIC_K];
	
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

	public SLIC() {
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
		
		case 2: {//SLIC
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			
			
			double S = Math.sqrt(w*h/SLIC_K);
			//Pixel[] clusterCenterArray = new Pixel[SLIC_K];
			
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
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
			for(int i=0; i<=h-1;i++){
				for(int j=0; j<=w-1; j++){
					if(i==0||i==h-1||j==0||j==w-1){
						sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH,sourcePixArray[i][j].deltaV);
					}
					else
						sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH+sourcePixArray[i][j-1].deltaH, sourcePixArray[i][j].deltaV+sourcePixArray[i-1][j].deltaV);
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
				}
			}
			
			//initialize cluster center
			int clusterIndex = 0;
			for (int i=0;i<h-S;i+=S){
				for(int j=0; j<w-S; j+=S){
					double minG = 99999999;
					Pixel minTemp = sourcePixArray[i][j];
					for(int m=i; m<i+S;m++){
						for(int n=j; n<j+S;n++){
							if(sourcePixArray[m][n].gradient.getLength()<=minG){
								minG = sourcePixArray[m][n].gradient.getLength();
								minTemp = sourcePixArray[m][n];
							}
						}
					}
					minTemp.isClusterCenter = true;
					minTemp.clusterIndex = clusterIndex;
					//clusterCenterArray[clusterIndex] = minTemp;
					clusterIndex++;
					//System.out.println("Index: "+ clusterIndex+ " X "+minTemp.getX()+" Y "+minTemp.getY() + " " + minTemp.clusterIndex);
				}
			}
			System.out.println("initialize cluster#:" + clusterIndex);
			/*
			//test Initialization
			for(int i=0; i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].isClusterCenter)
						biFiltered.setRGB(j, i, 16711680);
				}
			}
			
			*/
			int passNumber = 0;
			while(passNumber<TIMESTEPS){
				passNumber++;
				
				ArrayList<intVec2> centerList = new ArrayList<intVec2>();
				
				//re-center
				for(int i=0; i<h; i++){
					for(int j=0; j<w; j++){
						if(sourcePixArray[i][j].isClusterCenter){
							//turn off center
							sourcePixArray[i][j].isClusterCenter = false;
							
							//find the average of a cluster
							double avgWeight = 0;
							double sumR = 0;
							double sumG = 0;
							double sumB = 0;
							int pixCount = 0;
							for(int m=(int)(i-3*S);m<i+3*S;m++){
								for(int n=(int)(j-3*S); n<j+3*S;n++){
									if(m>=0&&n>=0&&m<h&&n<w){
										if(sourcePixArray[m][n].clusterIndex==sourcePixArray[i][j].clusterIndex){
											sumR+=sourcePixArray[m][n].getColor().getRed();
											sumG+=sourcePixArray[m][n].getColor().getGreen();
											sumB+=sourcePixArray[m][n].getColor().getBlue();
											avgWeight+=sourcePixArray[m][n].getWeight();
											pixCount++;
										}
									}
								}
							}
							sumR/=pixCount;
							sumG/=pixCount;
							sumB/=pixCount;
							avgWeight/=pixCount;
							
							//find the closest to the average
							Pixel newCenter = sourcePixArray[i][j];
							double minDist = 999999999;
							for(int m=(int)(i-3*S);m<i+3*S;m++){
								for(int n=(int)(j-3*S); n<j+3*S;n++){
									if(m>=0&&n>=0&&m<h&&n<w){
										if(sourcePixArray[m][n].clusterIndex==sourcePixArray[i][j].clusterIndex){
											double temp = calcColorDist(sourcePixArray[m][n],sumR,sumG,sumB);
											//double temp = Math.abs(sourcePixArray[m][n].getWeight()-avgWeight);
											if(temp<minDist){
												newCenter = sourcePixArray[m][n];
												minDist = temp;
											}
										}
									}
								}
							}
							//store the new centers;
							centerList.add(new intVec2(newCenter.getX(),newCenter.getY()));
						}
					}
				}
				
				while(!centerList.isEmpty()){
					intVec2 temp = centerList.remove(0);
					sourcePixArray[temp.y][temp.x].isClusterCenter = true;
				}//end re-center
				
				
				//cluster
				int clusterCounter = 0;
				for(int i=0; i<h; i++){
					for(int j=0; j<w; j++){
						if(sourcePixArray[i][j].isClusterCenter){
							for(int m=(int)(i-2*S);m<i+2*S;m++){
								for(int n=(int)(j-2*S); n<j+2*S;n++){
									if(m>=0&&n>=0&&m<h&&n<w&&!sourcePixArray[m][n].isClusterCenter){
										double temp = calcSLICdistance(sourcePixArray[i][j],sourcePixArray[m][n],S);
										if(sourcePixArray[m][n].getWeight()>temp){
											sourcePixArray[m][n].clusterIndex= sourcePixArray[i][j].clusterIndex;
											sourcePixArray[m][n].setWeight(temp);
										}
									}
								}
							}
							clusterCounter++;
						}
					}
				}
				//System.out.println("cluster#: "+ clusterCounter);
				
				
				
				
				
				//reset weight
				for(int i=0; i<h; i++){
					for(int j=0; j<w; j++){
						sourcePixArray[i][j].setWeight(99999999);
					}
				}
				System.out.println("Done pass: " + passNumber);
			}
			
			//test convergence
			for(int i=0; i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].isClusterCenter)
						biFiltered.setRGB(j, i, 16711680);
				}
			}
			
			//paint boundary
			for(int i=0; i<h;i++){
				for(int j=0;j<w;j++){
					int neighbourCount = 0;
					if(i>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i-1][j].clusterIndex)
						neighbourCount++;
					if(i<h-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i+1][j].clusterIndex)
						neighbourCount++;
					if(j>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i][j-1].clusterIndex)
						neighbourCount++;
					if(j<w-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i][j+1].clusterIndex)
						neighbourCount++;
					if(i>0&&j>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i-1][j-1].clusterIndex)
						neighbourCount++;
					if(i<h-1&&j<w-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i+1][j+1].clusterIndex)
						neighbourCount++;
					if(i<h-1&&j>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i+1][j-1].clusterIndex)
						neighbourCount++;
					if(i>0&&j<w-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i-1][j+1].clusterIndex)
						neighbourCount++;
					
					
					if(neighbourCount>1)
						biFiltered.setRGB(j, i, 0);
				}
			}
			
			break;
		}
		
		case 3:{ //new super pixel build
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			ArrayList<SuperPixel> superPixArray = new ArrayList<SuperPixel>();
			
			
			double S = Math.sqrt(w*h/SLIC_K);
			//Pixel[] clusterCenterArray = new Pixel[SLIC_K];
			
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
				}
			}
			System.out.println("Done calc pixel intensity");
			
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
			System.out.println("Done calc neighbouring pixel intensity");
			
			//pre-calc gradient
			for(int i=0; i<=h-1;i++){
				for(int j=0; j<=w-1; j++){
					if(i==0||i==h-1||j==0||j==w-1){
						sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH,sourcePixArray[i][j].deltaV);
					}
					else
						sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH+sourcePixArray[i][j-1].deltaH, sourcePixArray[i][j].deltaV+sourcePixArray[i-1][j].deltaV);
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
				}
			}
			System.out.println("Done calc gradient");
			
			//initialize cluster center
			int clusterIndex = 0;
			for (int i=0;i<h-S;i+=S){
				for(int j=0; j<w-S; j+=S){
					double minG = 99999999;
					Pixel minTemp = sourcePixArray[i][j];
					for(int m=i; m<i+S;m++){
						for(int n=j; n<j+S;n++){
							if(sourcePixArray[m][n].gradient.getLength()<=minG){
								minG = sourcePixArray[m][n].gradient.getLength();
								minTemp = sourcePixArray[m][n];
							}
						}
					}
					//minTemp.isClusterCenter = true;
					//minTemp.clusterIndex = clusterIndex;
					superPixArray.add(new SuperPixel(minTemp.getColor().getRed(),minTemp.getColor().getGreen(),minTemp.getColor().getBlue(),minTemp.getX(),minTemp.getY(),clusterIndex));
					//clusterCenterArray[clusterIndex] = minTemp;
					clusterIndex++;
					//System.out.println("Index: "+ clusterIndex+ " X "+minTemp.getX()+" Y "+minTemp.getY() + " " + minTemp.clusterIndex);
				}
			}
			System.out.println("initialize cluster#:" + clusterIndex);
			/*
			//test Initialization
			for(int i=0; i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].isClusterCenter)
						biFiltered.setRGB(j, i, 16711680);
				}
			}
			
			*/
			int passNumber = 0;
			while(passNumber<SLICTIMESTEPS){
				passNumber++;
				
				//clustering broadcast
				for(int i=0; i<superPixArray.size();i++){
					for(int m=(int)(superPixArray.get(i).y-2*S); m<superPixArray.get(i).y+2*S;m++){
						for(int n=(int)(superPixArray.get(i).x-2*S); n<superPixArray.get(i).x+2*S;n++){
							if(m>=0&&n>=0&&m<h&&n<w){
								double temp = calcSLICdistanceSuperPixel(superPixArray.get(i),sourcePixArray[m][n],S);
								if(sourcePixArray[m][n].getWeight()>temp){
									sourcePixArray[m][n].clusterIndex= superPixArray.get(i).index;
									sourcePixArray[m][n].setWeight(temp);
								}
							}
						}
					}
				}
				
				//re-center
				for(int i=0; i<superPixArray.size();i++){
					
					double sumR= 0;
					double sumG= 0;
					double sumB= 0;
					double sumX= 0;
					double sumY= 0;
					int neighbourCount= 0;
					
					for(int m=(int)(superPixArray.get(i).y-2*S); m<superPixArray.get(i).y+2*S;m++){
						for(int n=(int)(superPixArray.get(i).x-2*S); n<superPixArray.get(i).x+2*S;n++){
							if(m>=0&&n>=0&&m<h&&n<w){
								if(sourcePixArray[m][n].clusterIndex==superPixArray.get(i).index){
									sumR+=sourcePixArray[m][n].getColor().getRed();
									sumG+=sourcePixArray[m][n].getColor().getGreen();
									sumB+=sourcePixArray[m][n].getColor().getBlue();
									sumX+=sourcePixArray[m][n].getX();
									sumY+=sourcePixArray[m][n].getY();
									neighbourCount++;
								}
							}
						}
					}
					
					sumR/=neighbourCount;
					sumG/=neighbourCount;
					sumB/=neighbourCount;
					sumX/=neighbourCount;
					sumY/=neighbourCount;
					
					superPixArray.get(i).r = sumR;
					superPixArray.get(i).g = sumG;
					superPixArray.get(i).b = sumB;
					superPixArray.get(i).x = sumX;
					superPixArray.get(i).y = sumY;
					
				}
				
				//reset weight and index
				for(int i=0; i<h; i++){
					for(int j=0; j<w; j++){
						sourcePixArray[i][j].setWeight(99999999);
						//sourcePixArray[i][j].clusterIndex = 0;
					}
				}
				System.out.println("Done pass: " + passNumber);
			}
			
			System.out.println("Done SLIC");
			
			/*//print SLIC
			//test convergence
			for(int i=0; i<superPixArray.size();i++){
				biFiltered.setRGB((int)(superPixArray.get(i).x),(int)(superPixArray.get(i).y), 16711680);
			}
			
			//paint boundary
			for(int i=0; i<h;i++){
				for(int j=0;j<w;j++){
					int neighbourCount = 0;
					if(i>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i-1][j].clusterIndex)
						neighbourCount++;
					if(i<h-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i+1][j].clusterIndex)
						neighbourCount++;
					if(j>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i][j-1].clusterIndex)
						neighbourCount++;
					if(j<w-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i][j+1].clusterIndex)
						neighbourCount++;
					if(i>0&&j>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i-1][j-1].clusterIndex)
						neighbourCount++;
					if(i<h-1&&j<w-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i+1][j+1].clusterIndex)
						neighbourCount++;
					if(i<h-1&&j>0&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i+1][j-1].clusterIndex)
						neighbourCount++;
					if(i>0&&j<w-1&&sourcePixArray[i][j].clusterIndex!=sourcePixArray[i-1][j+1].clusterIndex)
						neighbourCount++;
					
					if(neighbourCount>2)
						biFiltered.setRGB(j, i, 0);
				}
			}
			*/
			
			
			//redistribute spring length
			//redistributeSpringLength(sourcePixArray, superPixArray);	
			
			
			
			//resolve spring force
			//populate rx ry, original pixel's real position on upsampled image
			Random rand = new Random();
			for(int i =0; i<superPixArray.size();i++){
				superPixArray.get(i).randLength = 0.1+0.9*rand.nextDouble();
			}
			
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					
					/*
					//distribution
					double r = superPixArray.get(sourcePixArray[i][j].clusterIndex).r;
					double g = superPixArray.get(sourcePixArray[i][j].clusterIndex).g;
					double b = superPixArray.get(sourcePixArray[i][j].clusterIndex).b;
					//0.30R + 0.59G + 0.11B
					double lum = 0.3*r/255.0 + 0.59*g/255.0+0.11*b/255.0;
					sourcePixArray[i][j].restLengthH = 0.1 + lum * 0.9;
					sourcePixArray[i][j].restLengthV = 0.1 + lum * 0.9;
					*/
					
					//Random
					sourcePixArray[i][j].restLengthH = superPixArray.get(sourcePixArray[i][j].clusterIndex).randLength;
					sourcePixArray[i][j].restLengthV = sourcePixArray[i][j].restLengthH;
					//System.out.println("restLength: i" +i+" j "+j+" "+ sourcePixArray[i][j].restLengthH);
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
						//forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenPixelCurrentAndOriginal(sourcePixArray[i][j]));
						
						//System.out.print(" "+forceSum.x+" "+forceSum.y);
						forceNorm = Vec2.normalize(forceSum);
						moveDist = forceSum.getLength();
						if (moveDist>0.2)
							moveDist = 0.2;
						//System.out.print(" "+moveDist);
						sourcePixArray[i][j].rx += forceNorm.x * moveDist;
						sourcePixArray[i][j].ry += forceNorm.y * moveDist;
						//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					}
				}
				System.out.println("Done time step: " + timeStep);
			}
			
			triangleBarycentric(sourcePixArray);
			
			break;
			
		}
		
		case 4: {//length smoothing
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			ArrayList<SuperPixel> superPixArray = new ArrayList<SuperPixel>();
			
			
			double S = Math.sqrt(w*h/SLIC_K);
			//Pixel[] clusterCenterArray = new Pixel[SLIC_K];
			
			
			//precalc intensity
			for(int i=1; i<h-1;i++){
				for(int j=1; j<w-1; j++){
					sourcePixArray[i][j].intensity = sourcePixArray[i][j].getIntensity();
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
				}
			}
			System.out.println("Done calc pixel intensity");
			
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
			System.out.println("Done calc neighbouring pixel intensity");
			
			//pre-calc gradient
			for(int i=0; i<=h-1;i++){
				for(int j=0; j<=w-1; j++){
					if(i==0||i==h-1||j==0||j==w-1){
						sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH,sourcePixArray[i][j].deltaV);
					}
					else
						sourcePixArray[i][j].gradient = new Vec2(sourcePixArray[i][j].deltaH+sourcePixArray[i][j-1].deltaH, sourcePixArray[i][j].deltaV+sourcePixArray[i-1][j].deltaV);
					//System.out.println(" "+sourcePixArray[i][j].gradient.x+" "+sourcePixArray[i][j].gradient.y);
				}
			}
			System.out.println("Done calc gradient");
			
			//initialize cluster center
			int clusterIndex = 0;
			for (int i=0;i<h-S;i+=S){
				for(int j=0; j<w-S; j+=S){
					double minG = 99999999;
					Pixel minTemp = sourcePixArray[i][j];
					for(int m=i; m<i+S;m++){
						for(int n=j; n<j+S;n++){
							if(sourcePixArray[m][n].gradient.getLength()<=minG){
								minG = sourcePixArray[m][n].gradient.getLength();
								minTemp = sourcePixArray[m][n];
							}
						}
					}
					//minTemp.isClusterCenter = true;
					//minTemp.clusterIndex = clusterIndex;
					superPixArray.add(new SuperPixel(minTemp.getColor().getRed(),minTemp.getColor().getGreen(),minTemp.getColor().getBlue(),minTemp.getX(),minTemp.getY(),clusterIndex));
					//clusterCenterArray[clusterIndex] = minTemp;
					clusterIndex++;
					//System.out.println("Index: "+ clusterIndex+ " X "+minTemp.getX()+" Y "+minTemp.getY() + " " + minTemp.clusterIndex);
				}
			}
			System.out.println("initialize cluster#:" + clusterIndex);
			/*
			//test Initialization
			for(int i=0; i<h;i++){
				for(int j=0;j<w;j++){
					if(sourcePixArray[i][j].isClusterCenter)
						biFiltered.setRGB(j, i, 16711680);
				}
			}
			
			*/
			int passNumber = 0;
			while(passNumber<SLICTIMESTEPS){
				passNumber++;
				
				//clustering broadcast
				for(int i=0; i<superPixArray.size();i++){
					for(int m=(int)(superPixArray.get(i).y-2*S); m<superPixArray.get(i).y+2*S;m++){
						for(int n=(int)(superPixArray.get(i).x-2*S); n<superPixArray.get(i).x+2*S;n++){
							if(m>=0&&n>=0&&m<h&&n<w){
								double temp = calcSLICdistanceSuperPixel(superPixArray.get(i),sourcePixArray[m][n],S);
								if(sourcePixArray[m][n].getWeight()>temp){
									sourcePixArray[m][n].clusterIndex= superPixArray.get(i).index;
									sourcePixArray[m][n].setWeight(temp);
								}
							}
						}
					}
				}
				
				//re-center
				for(int i=0; i<superPixArray.size();i++){
					
					double sumR= 0;
					double sumG= 0;
					double sumB= 0;
					double sumX= 0;
					double sumY= 0;
					int neighbourCount= 0;
					
					for(int m=(int)(superPixArray.get(i).y-2*S); m<superPixArray.get(i).y+2*S;m++){
						for(int n=(int)(superPixArray.get(i).x-2*S); n<superPixArray.get(i).x+2*S;n++){
							if(m>=0&&n>=0&&m<h&&n<w){
								if(sourcePixArray[m][n].clusterIndex==superPixArray.get(i).index){
									sumR+=sourcePixArray[m][n].getColor().getRed();
									sumG+=sourcePixArray[m][n].getColor().getGreen();
									sumB+=sourcePixArray[m][n].getColor().getBlue();
									sumX+=sourcePixArray[m][n].getX();
									sumY+=sourcePixArray[m][n].getY();
									neighbourCount++;
								}
							}
						}
					}
					
					sumR/=neighbourCount;
					sumG/=neighbourCount;
					sumB/=neighbourCount;
					sumX/=neighbourCount;
					sumY/=neighbourCount;
					
					superPixArray.get(i).r = sumR;
					superPixArray.get(i).g = sumG;
					superPixArray.get(i).b = sumB;
					superPixArray.get(i).x = sumX;
					superPixArray.get(i).y = sumY;
					
				}
				
				//reset weight and index
				for(int i=0; i<h; i++){
					for(int j=0; j<w; j++){
						sourcePixArray[i][j].setWeight(99999999);
						//sourcePixArray[i][j].clusterIndex = 0;
					}
				}
				System.out.println("Done pass: " + passNumber);
			}
			
			System.out.println("Done SLIC");
			
			
			
			
			
			//resolve spring force
			//populate rx ry, original pixel's real position on upsampled image
			Random rand = new Random();
			for(int i =0; i<superPixArray.size();i++){
				superPixArray.get(i).randLength = 0.1+0.9*rand.nextDouble();
			}
			
			for(int i = 0; i < h; i++){
				for(int j=0; j<w; j++){
					sourcePixArray[i][j].rx = j*resizeScale;
					sourcePixArray[i][j].ry = i*resizeScale;
					//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					
					/*
					//distribution
					double r = superPixArray.get(sourcePixArray[i][j].clusterIndex).r;
					double g = superPixArray.get(sourcePixArray[i][j].clusterIndex).g;
					double b = superPixArray.get(sourcePixArray[i][j].clusterIndex).b;
					//0.30R + 0.59G + 0.11B
					double lum = 0.3*r/255.0 + 0.59*g/255.0+0.11*b/255.0;
					sourcePixArray[i][j].restLengthH = 0.1 + lum * 0.9;
					sourcePixArray[i][j].restLengthV = 0.1 + lum * 0.9;
					*/
					
					//Random
					sourcePixArray[i][j].restLengthH = superPixArray.get(sourcePixArray[i][j].clusterIndex).randLength;
					sourcePixArray[i][j].restLengthV = sourcePixArray[i][j].restLengthH;
					//System.out.println("restLength: i" +i+" j "+j+" "+ sourcePixArray[i][j].restLengthH);
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
						
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j-1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i][j+1]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i-1][j]));
						forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenTwoPixelSLIC(sourcePixArray, sourcePixArray[i][j], sourcePixArray[i+1][j]));
						//forceSum = Vec2.Vec2Add(forceSum, calcForceBetweenPixelCurrentAndOriginal(sourcePixArray[i][j]));
						
						//System.out.print(" "+forceSum.x+" "+forceSum.y);
						forceNorm = Vec2.normalize(forceSum);
						moveDist = forceSum.getLength();
						if (moveDist>0.2)
							moveDist = 0.2;
						//System.out.print(" "+moveDist);
						sourcePixArray[i][j].rx += forceNorm.x * moveDist;
						sourcePixArray[i][j].ry += forceNorm.y * moveDist;
						//biFiltered.setRGB(Tools.round(sourcePixArray[i][j].rx), Tools.round(sourcePixArray[i][j].ry), 16711680); //red
					}
				}
				System.out.println("Done time step: " + timeStep);
			}
			
			triangleBarycentric(sourcePixArray);
			
			break;
		}
			
		
		}
	}
	
	private double calcSLICdistanceSuperPixel(SuperPixel sp,
			Pixel p, double S) {
		double eudist = Math.sqrt((sp.x-p.getX())*(sp.x-p.getX()) + (sp.y-p.getY())*(sp.y-p.getY()));
		double colorDist = Math.sqrt(Math.pow(sp.r-p.getColor().getRed(), 2)+
				Math.pow(sp.g-p.getColor().getGreen(), 2)+
				Math.pow(sp.b-p.getColor().getBlue(), 2));
		return colorDist+150/S*eudist;
	}

	private double calcSLICdistance(Pixel p1, Pixel p2, double S) {
		// TODO Auto-generated method stub
		double eudist = Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + (p1.getY()-p2.getY())*(p1.getY()-p2.getY()));
		double colorDist = Math.sqrt(Math.pow(p1.getColor().getRed()-p2.getColor().getRed(), 2)+
									Math.pow(p1.getColor().getGreen()-p2.getColor().getGreen(), 2)+
									Math.pow(p1.getColor().getBlue()-p2.getColor().getBlue(), 2));
		
		return colorDist+100/S*eudist;
		//return eudist;
	}

	private double calcColorDist(Pixel p, double sumR, double sumG,
			double sumB) {
		// TODO Auto-generated method stub
		return (p.getColor().getRed()-sumR)*(p.getColor().getRed()-sumR) +
				(p.getColor().getBlue()-sumB)*(p.getColor().getBlue()-sumB)+
				(p.getColor().getGreen()-sumG)*(p.getColor().getGreen()-sumG);
	}
	
	private static double[][] createGaussianKernel(int w){
		double[][] GK = new double[w][w];
		int center = w/2;
		for(int i =0;i<w;i++){
			for (int j=0;j<w;j++){
				Vec2 disp = new Vec2(i-center,j-center);
				double dist = disp.getLength();
				//Gwd=(exp(-(gw.^2)/(2*(sigd^2))))
				GK[i][j] = Math.exp(-(dist*dist)/(2*SIGD*SIGD));
				System.out.print(" " + GK[i][j]);
			}
			System.out.println();
		}
		
		return GK;
	}

	
	public static double angleBetweenTwoVector(Vec2 v1, Vec2 v2) {
		if(v1.getLength()==0||v2.getLength()==0)
			return 3.1415926*0.5;
		else
			return Math.acos((v1.x*v2.x + v1.y*v2.y)/(v1.getLength()*v2.getLength()));
	}

	

	
	private static Vec2 calcForceBetweenTwoPixelSLIC(Pixel[][] sourcePixArray, Pixel center, Pixel out){
		Vec2 displacement = new Vec2(out.rx-center.rx, out.ry - center.ry);
		Vec2 direction = Vec2.normalize(displacement);
		
		double rl = 0.5*(center.restLengthH+out.restLengthH);
		double force = (displacement.getLength()-rl)*springK;
		force -= force * damperC;		
		
		return new Vec2(direction.x*force, direction.y*force);
	}
	
	
	
	static double rand(Vec2 co){
	    return fract(Math.sin(Vec2.dot(co , new Vec2(12.9898,78.233))) * 43758.5453);
	}
	
	static double fract(double a){
		return a-(int)a;
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
		double [][] G = createGaussianKernel(5);
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
		final SLIC si = new SLIC();
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