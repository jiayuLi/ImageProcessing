

import java.io.*;
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

public class Geo_recoloring extends Component implements ActionListener {

	String descs[] = { "Original", "Geodesic/w/residual enhanced", "Geodesic/w/residual",
			"Geodesic/w", "Geodesic/c", "Geo/recoloring",
			"upsample+geodesic+origpix", "upsample+geodesic/w+allpix" };

	final String IMAGESOURCE = "texim/dragonfly.png";

	final int maskSize = 200;
	final int upsampleMaskSize = 10;
	final double R = 1;
	final double resizeScale = 6;
	//final double exponentialPower = 7;
	//final int numOfPotEntrance = 10;
	final int incrementalWeightInPot = 1;
	final int potEntranceDensity = 30; //higher - less dense
	final Color potPaintColor = Color.BLACK;

	int opIndex;
	private BufferedImage bi, biFiltered, background;
	int w, h;
	static JFrame f;
	static JPanel p1;
	static int mouseX = 0;
	static int mouseY = 0;

	static JCheckBox jcb = new JCheckBox("show mask");
	static JTextField jtf = new JTextField(
			"This is a text field for display some info", 20);
	static JTextField jtf_InpotPixCount = new JTextField("Pix#InPot", 10);

	public static final float[] SHARPEN3x3 = { // sharpening filter kernel
	0.f, -1.f, 0.f, -1.f, 5.f, -1.f, 0.f, -1.f, 0.f };

	public static final float[] BLUR3x3 = { 0.1f, 0.1f, 0.1f, // low-pass filter
																// kernel
			0.1f, 0.2f, 0.1f, 0.1f, 0.1f, 0.1f };

	public Geo_recoloring() {
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

		case 1: {
		}
			
		case 2: {
		}

		case 3: {
		}
		
		case 4: /* geodesic correct */{
			biFiltered = ImageIO.read(new File(IMAGESOURCE));
			int[][] intPixArray = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray = convert2DIntArrayToPixelArray(intPixArray);
			Pixel[][] resultPixArray = new Pixel[h][w];
			LinkedList<Pixel> pixStorage = new LinkedList<Pixel>();
			PriorityQueue<Pixel> maskPriorityQueue = new PriorityQueue<Pixel>();
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
					// clean up storage & priority queue
					pixStorage.clear();
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
							Pixel right = sourcePixArray[temp.getY()][temp
									.getX() + 1];
							if (!right.isUsedByCurrentMask()) { // dr*dr + dg*dg
																// + db*db
								double rangeDistance = intensityDiff(right.getColor(), sourcePixArray[i][j].getColor());
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
							Pixel top = sourcePixArray[temp.getY() - 1][temp
									.getX()];
							if (!top.isUsedByCurrentMask()) { // dr*dr + dg*dg +
																// db*db
								double rangeDistance = intensityDiff(top.getColor(), sourcePixArray[i][j].getColor());
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
							Pixel bottom = sourcePixArray[temp.getY() + 1][temp
									.getX()];
							if (!bottom.isUsedByCurrentMask()) { // dr*dr +
																	// dg*dg +
																	// db*db
								double rangeDistance = intensityDiff(bottom.getColor(), sourcePixArray[i][j].getColor());
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
						if (jcb.isSelected()) {
							biFiltered.setRGB(temp.getX(), temp.getY(),
									16711680); // red = 16711680
						}
						temp.setUsedByCurrentMask(false);
						temp.setUsedByCurrentPQ(false);
					}
					sumR /= maskSize;
					sumG /= maskSize;
					sumB /= maskSize;

					Color tempColor = new Color(sumR, sumG, sumB);

					biFiltered.setRGB(j, i, getIntFromColor(tempColor));
					
					if (jcb.isSelected()) {
						i = h;
						j = w;
					}

				}
				System.out.println("i" + i);
			}
			break;
		}
		case 5: {
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
		final Geo_recoloring si = new Geo_recoloring();
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