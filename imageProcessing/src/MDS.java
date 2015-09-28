

import java.io.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeSet;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;

import javax.imageio.*;
import javax.swing.*;
import mdsj.MDSJ;

public class MDS extends Component implements ActionListener {

	String descs[] = { "Original", "bicubic upsampled", "MDS even spacing",
			"median filter", "median filter on upsampled", "bilateral roundup",
			"placeholder", "placeholder" };

	final String IMAGESOURCE = "multipass/picTable_3.png";

	//final int maskSize = 200;
	final int upsampleMaskSize = 5;
	static double maskSize = 9;
	static int regionWH = 9;
	final double R = 1;
	final int resizeScale = 4;
	int opIndex;
	private BufferedImage bi, biFiltered, background;
	int w, h;
	static JFrame f;
	static JPanel p1;
	static int mouseX = 0;
	static int mouseY = 0;
	static double gamma = 0.1;
	//================================
	static final int PATCHDIAMETER = 11; //width and hight of a square shaped patch
	//================================

	static JCheckBox jcb = new JCheckBox("show mask");
	static JTextField jtf = new JTextField(
			"This is a text field for display some info", 20);
	static JTextField jtf_InpotPixCount = new JTextField("Pix#InPot", 10);

	public static final float[] SHARPEN3x3 = { // sharpening filter kernel
	0.f, -1.f, 0.f, -1.f, 5.f, -1.f, 0.f, -1.f, 0.f };

	public static final float[] BLUR3x3 = { 0.1f, 0.1f, 0.1f, // low-pass filter
																// kernel
			0.1f, 0.2f, 0.1f, 0.1f, 0.1f, 0.1f };

	public MDS() {
		try {
			bi = ImageIO.read(new File(IMAGESOURCE));
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			if (bi.getType() != BufferedImage.TYPE_INT_RGB) {
				BufferedImage bi2 = new BufferedImage(w, h,
						BufferedImage.TYPE_INT_RGB);
				Graphics big = bi2.getGraphics();
				big.drawImage(bi, 0, 0, null);
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
	}

	int lastOp;

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

		case 1: /*bicubic upsampled */{
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			break;
		}
			
		case 2: { //MDS testing default spacing
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			
			int currW = 0; 
			int currH = 0;
			
			while(currH<h){
				//this is one patch
				
				//3 anchor points, one top, one bottom, one left
				int patchCenterX = currW + PATCHDIAMETER/2 ;
				int patchCenterY = currH + PATCHDIAMETER/2 ;
				int topX = patchCenterX;
				int topY = patchCenterY - 2*PATCHDIAMETER;
				int bottomX = patchCenterX;
				int bottomY = patchCenterY + 2*PATCHDIAMETER;
				int leftX = patchCenterX - 2*PATCHDIAMETER;
				int leftY = patchCenterY;
				int rightX = patchCenterX + 2*PATCHDIAMETER;
				int rightY = patchCenterY;
				
				boolean turnsBefore = turns(bottomX,bottomY,topX,topY,leftX,leftY)<0;
				
				double[][] MDSMatrix = new double[PATCHDIAMETER*PATCHDIAMETER+4][PATCHDIAMETER*PATCHDIAMETER+4];
				int rowNumber = 0; //each row is one pixel's distances to all other pixels
				for(int i = currH; i<currH+PATCHDIAMETER; i++){
					for(int j=currW; j<currW+PATCHDIAMETER; j++){
						//distances within patch
						for(int p = currH; p<currH+PATCHDIAMETER; p++){
							for(int q = currW; q<currW+PATCHDIAMETER; q++){
								MDSMatrix[rowNumber][(p-currH)*PATCHDIAMETER+(q-currW)] = Math.sqrt((i-p)*(i-p)+(j-q)*(j-q));
							}
						}
						//distance to anchors
						MDSMatrix[rowNumber][PATCHDIAMETER*PATCHDIAMETER] = Math.sqrt((i-topY)*(i-topY)+(j-topX)*(j-topX));
						MDSMatrix[rowNumber][PATCHDIAMETER*PATCHDIAMETER+1] = Math.sqrt((i-bottomY)*(i-bottomY)+(j-bottomX)*(j-bottomX));
						MDSMatrix[rowNumber][PATCHDIAMETER*PATCHDIAMETER+2] = Math.sqrt((i-leftY)*(i-leftY)+(j-leftX)*(j-leftX));
						MDSMatrix[rowNumber][PATCHDIAMETER*PATCHDIAMETER+3] = Math.sqrt((i-rightY)*(i-rightY)+(j-rightX)*(j-rightX));
						rowNumber++;
					}
				}
				
				
				int n=MDSMatrix[0].length;    // number of data objects
				double[][] output=MDSJ.classicalScaling(MDSMatrix); // apply MDS
				//check if it's mirrored 
				//from bottom-top to left should be left turn
				double newBottomX = output[0][n-3];
				double newBottomY = output[1][n-3];
				double newTopX = output[0][n-4];
				double newTopY = output[1][n-4];
				double newLeftX = output[0][n-2];
				double newLeftY = output[1][n-2];
				double newRightX = output[0][n-1];
				double newRightY = output[1][n-1];
				double newCenterX = (newTopX+newBottomX)/2;
				double newCenterY = (newTopY+newBottomY)/2;
				boolean turnsAfter = turns(newBottomX,newBottomY,newTopX,newTopY,newLeftX,newLeftY)<0;
				
				if(turnsBefore!=turnsAfter){//if mirrored, flip
					for(int i=0;i<n;i++){
						output[0][i] = newCenterX - (output[0][i] - newCenterX) ;
					}
					newBottomX = output[0][n-3];
					newBottomY = output[1][n-3];
					newTopX = output[0][n-4];
					newTopY = output[1][n-4];
					newLeftX = output[0][n-2];
					newLeftY = output[1][n-2];
					newRightX = output[0][n-1];
					newRightY = output[1][n-1];
					newCenterX = (newTopX+newBottomX)/2;
					newCenterY = (newTopY+newBottomY)/2;
				}
				
				//find orientation using newLeft newRight
				double orientation = 0.0;
				double DistanceleftToRight = Math.sqrt((newRightX-newLeftX)*(newRightX-newLeftX)+(newRightY-newLeftY)*(newRightY-newLeftY));
				if(newRightX>newLeftX){ //   -90<theta<90
						orientation = Math.asin((newLeftY-newRightY)/DistanceleftToRight);
				}
				else{
					if(newRightY<newLeftY)
						orientation = 3.1415926 - Math.asin((newLeftY-newRightY)/DistanceleftToRight);
					else
						orientation = -3.1415926 - Math.asin((newLeftY-newRightY)/DistanceleftToRight);
				}
					
				
				//we want to orient back -orientation angle
				for(int i=0;i<n;i++){
					double tempX = output[0][i];
					double tempY = output[1][i];
					double currOrientation = 0.0;
					
					double D = Math.sqrt((tempX-newCenterX)*(tempX-newCenterX)+(tempY-newCenterY)*(tempY-newCenterY));
					
					if(newCenterX<tempX) //   -90<theta<90
						currOrientation = Math.asin((newCenterY-tempY)/D);
					else{
						if(tempY<newCenterY)
							currOrientation = 3.1415926 - Math.asin((newCenterY-tempY)/D);
						else
							currOrientation = -3.1415926 - Math.asin((newCenterY-tempY)/D);
					}
						
					
					double targetOrientation = currOrientation - orientation;
					
					System.out.println(//"tempx"+tempX+"tempY"+tempY+"centerX"+newCenterX+"centerY"+newCenterY+
							"globalOrient"+orientation*180/3.14159+"currOrient"+currOrientation*180/3.14159
							+"tarOrient"+targetOrientation*180/3.14159);
					
					
					output[0][i] = D * Math.cos(targetOrientation);
					output[1][i] = D * Math.sin(targetOrientation);
				}
				System.out.println("newLeftX"+newLeftX+"y"+newLeftY+"newRightX"+newRightX+"y"+newRightY);
				
				System.out.println("done one patch");
				
				for(int i=0; i<n; i++) {  // output all coordinates
				    //System.out.println(output[0][i]+" "+output[1][i]);
					int x = (int)((output[0][i]+currW)*resizeScale);
					int y = (int)((output[1][i]+currH)*resizeScale);
					//System.out.println("x "+x+" y "+y);
					x=x<0?0:x;
					y=y<0?0:y;
					x=x>((w-1)*resizeScale)?((w-1)*resizeScale):x;
					y=y>((h-1)*resizeScale)?((h-1)*resizeScale):y;
					if(i==n-4)
						biFiltered.setRGB(x, y, 255*65536+0*256+0);//red
					else if(i==n-3)
						biFiltered.setRGB(x, y, 0*65536+255*256+0);//green
					else if(i==n-2)
						biFiltered.setRGB(x, y, 0*65536+0*256+255);//blue
					else if(i==n-1)
						biFiltered.setRGB(x, y, 255*65536+255*256+0);//yellow
					else
						biFiltered.setRGB(x, y, 16711680);
				}
				
				currW+=PATCHDIAMETER;
				if(currW>w){
					currW = 0;
					currH+=PATCHDIAMETER;
				}
			}
			
			
			break;
		}

		case 3: {// fractional median filter
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
			double R = 0;
			double G = 0;
			double B = 0;
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
									.setWeight(sourcePixArray[(int)startingY+n][(int)startingX+m].getIntensity());
							pixStorage.add(sourcePixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					R = 0;
					G = 0;
					B = 0;
					int size = pixStorage.size();
					while(true){
						Pixel temp = pixStorage.poll();
						if(pixStorage.size()<(size/2)){
							R = temp.getColor().getRed();
							G = temp.getColor().getGreen();
							B = temp.getColor().getBlue();
							break;
						}
					}
					
					Color tempColor = new Color((int)R, (int)G, (int)B);
					Pixel tempPixel  = new Pixel(1,1,tempColor,1);
					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					pixStorage.clear();
				}
				System.out.println("i" + i );
			}
			
			
			break;
		}
		
		case 4: /* median on upsampled */{
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
			double R = 0;
			double G = 0;
			double B = 0;
			for (int i = 0; i < h*resizeScale; i++) {
				for (int j = 0; j < w*resizeScale; j++) {
					
					
					double fractionJ = j;///resizeScale;
					double fractionI = i;///resizeScale;
					
					if(fractionJ<(regionWH/2))
						startingX = 0;
					else if (fractionJ>(w*resizeScale-1-(regionWH/2)))
						startingX = w*resizeScale-1-regionWH;
					else
						startingX = fractionJ - regionWH/2;
					
					if(fractionI<(regionWH/2))
						startingY = 0;
					else if (fractionI > h*resizeScale-1-(regionWH/2))
						startingY = h*resizeScale-1-regionWH;
					else 
						startingY = fractionI - regionWH/2;
					
					
					for (int m = 0; m<regionWH; m++){
						for( int n = 0; n<regionWH; n++){
							upsampledPixArray[(int)startingY+n][(int)startingX+m]
									.setWeight(upsampledPixArray[(int)startingY+n][(int)startingX+m].getIntensity());
							pixStorage.add(upsampledPixArray[(int)startingY+n][(int)startingX+m]);
						}
					}
					
					R = 0;
					G = 0;
					B = 0;
					int size = pixStorage.size();
					while(true){
						Pixel temp = pixStorage.poll();
						if(pixStorage.size()<(size/2)){
							R = temp.getColor().getRed();
							G = temp.getColor().getGreen();
							B = temp.getColor().getBlue();
							break;
						}
					}
					
					Color tempColor = new Color((int)R, (int)G, (int)B);
					//Pixel tempPixel  = new Pixel(1,1,tempColor,1);
					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					pixStorage.clear();
				}
				System.out.println("i" + i );
			}
			
			
			break;
		}
		case 5: {
			
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
			

		case 6: /*  */{
		}
			
			
			
		case 7:{
			
		}
		
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
	
	static double turns (Pixel b, Pixel p, Pixel q){
		return (p.rx - b.rx)*(-q.ry + b.ry)-(-p.ry +b.ry)*(q.rx - b.rx);
	}
	
	static double turns (double newBottomX, double newBottomY, double newTopX, double newTopY, double newLeftX, double newLeftY){
		return (newTopX - newBottomX)*(-newLeftY + newBottomY)-(-newTopY +newBottomY)*(newLeftX - newBottomX);
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
	
	private static int calculateBilateralDistance (Pixel p1, Pixel p2){
		int Xdiff = p1.getX() - p2.getX();
		int Ydiff = p1.getY() - p2.getY();
		int intensityDiff = (int)(Math.pow((p1.getColor().getRed() - p2.getColor().getRed()),2) + 
						Math.pow((p1.getColor().getGreen() - p2.getColor().getGreen()),2)+
						Math.pow((p1.getColor().getBlue() - p2.getColor().getBlue()),2));
		double temp = Xdiff*Xdiff+Ydiff*Ydiff+ gamma * intensityDiff ;//+ avgTexturenessMap[p1.getY()][p1.getX()];
		
		return (int)Math.sqrt(temp);
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

	public static void main(String s[]) {
		f = new JFrame("Geodesic Filter & Upsampling");
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		// p1 = new JPanel();
		final MDS si = new MDS();
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
		panel.add(new JLabel("#PixInPot"));
		panel.add(jtf_InpotPixCount);
		f.add("South", panel);
		f.pack();
		f.setVisible(true);
		f.setExtendedState(f.getExtendedState() | JFrame.MAXIMIZED_BOTH);

		f.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				mouseX = e.getX() - 8;
				mouseY = e.getY() - 30;
				jtf.setText("" + mouseX + ", " + mouseY);
				si.repaint();
			}
		});

	}
}