//upsampling

import java.io.*;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;

import javax.imageio.*;
import javax.swing.*;

public class GeodesicFilterV2 extends Component implements ActionListener {

	String descs[] = { "Original", "upsample", "Convolve : Sharpen",
			"upsampling + ranged geodesic/c + original as base", "Geodesic/c", "upsample+geodesic/w",
	"upsample+geodesic/c" };

	final String IMAGESOURCE = "treebark_small.jpg";

	final int maskSize = 300;
	final int upsampleMaskSize = 6;
	final double R = 1;
	final int resizeScale = 6;

	int opIndex;
	private BufferedImage bi, biFiltered;
	int w, h;
	static JFrame f;
	static JPanel p1;
	static int mouseX = 0;
	static int mouseY = 0;

	static JCheckBox jcb = new JCheckBox("show mask");
	static JTextField jtf = new JTextField(
			"This is a text field for display some info", 20);

	public static final float[] SHARPEN3x3 = { // sharpening filter kernel
		0.f, -1.f, 0.f, -1.f, 5.f, -1.f, 0.f, -1.f, 0.f };

	public static final float[] BLUR3x3 = { 0.1f, 0.1f, 0.1f, // low-pass filter
		// kernel
		0.1f, 0.2f, 0.1f, 0.1f, 0.1f, 0.1f };

	public GeodesicFilterV2() {
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
		case 1: /* upsample, bicubic */
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			break;
		case 2: /* sharpen */
			float[] data = (opIndex == 1) ? BLUR3x3 : SHARPEN3x3;
			op = new ConvolveOp(new Kernel(3, 3, data), ConvolveOp.EDGE_NO_OP,
					null);
			biFiltered = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			op.filter(bi, biFiltered);
			break;

		case 3: {/* upsampling + ranged geodesic/c + original as base */
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			
			int[][] intPixArray1 = convertTo2DUsingGetRGB(bi);
			Pixel[][] sourcePixArray1 = convert2DIntArrayToPixelArray(intPixArray1);
			
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray2 = convert2DIntArrayToPixelArray(intPixArray2);
			Pixel[][] resultPixArray2 = new Pixel[h][w];
			LinkedList<Pixel> pixStorage2 = new LinkedList<Pixel>();
			PriorityQueue<Pixel> maskPriorityQueue2 = new PriorityQueue<Pixel>();
			for (int i = 0; i < h * resizeScale; i++) {
				for (int j = 0; j < w * resizeScale; j++) {

					if (jcb.isSelected()) {
						if (mouseX >= w * resizeScale
								|| mouseY >= h * resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale + 1);
							j = (int) (w * resizeScale + 1);
							continue;
						}
						else {
							j = mouseX;
							i = mouseY;
						}
					}

					// clean up storage & priority queue
					pixStorage2.clear();
					maskPriorityQueue2.clear();

					/*
					 * //reset the weight of all of the pixels for(int m=0; m<h;
					 * m++) for(int n=0; n<w; n++)
					 * sourcePixArray[m][n].setWeight(999999999);
					 */
					// start populating the mask
					sourcePixArray2[i][j].setWeight(0);
					maskPriorityQueue2.add(sourcePixArray2[i][j]);
					//from the upsampled image, find the original pixel that is closest to the current mask base
					int remainderX = j % resizeScale;
					int remainderY = i % resizeScale;
					int threshold = resizeScale/2;
					Pixel closestOrigPix = new Pixel(0, 0, Color.BLACK, 99999999);
					if(remainderX>threshold&&remainderY<=threshold&&j<resizeScale*(w-1))
						closestOrigPix = sourcePixArray1[i/resizeScale][j/resizeScale + 1];
					else if(remainderX<=threshold&&remainderY>threshold&&i<resizeScale*(h-1))
						closestOrigPix = sourcePixArray1[i/resizeScale+1][j/resizeScale];
					else if(remainderX>threshold&&remainderY>threshold&&i<resizeScale*(h-1)&&j<resizeScale*(w-1))
						closestOrigPix = sourcePixArray1[i/resizeScale + 1][j/resizeScale + 1];
					else
						closestOrigPix = sourcePixArray1[i/resizeScale][j/resizeScale];
					
					int origPixCount = 0;
					while (origPixCount < maskSize) {
						// get the lowest weighted pixel from the priority queue
						Pixel temp = maskPriorityQueue2.remove();
						// find the four pixels adjacent to the selected pixel
						// the one on the left
						if (temp.getX() > 0) {
							Pixel left = sourcePixArray2[temp.getY()][temp
							                                          .getX() - 1];

							if (!left.isUsedByCurrentMask()) { 

								double rangeDistance = intensityDiff(left.getColor(), closestOrigPix.getColor());
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
									maskPriorityQueue2.add(left);
								}
							}
						}
						// the one on the right
						if (temp.getX() < (w * resizeScale - 1)) {
							Pixel right = sourcePixArray2[temp.getY()][temp
							                                           .getX() + 1];
							if (!right.isUsedByCurrentMask()) { 

								double rangeDistance = intensityDiff(right.getColor(), closestOrigPix.getColor());
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
									maskPriorityQueue2.add(right);
								}
							}
						}
						// the one on top
						if (temp.getY() > 0) {
							Pixel top = sourcePixArray2[temp.getY() - 1][temp
							                                             .getX()];
							if (!top.isUsedByCurrentMask()) { 

								double rangeDistance = intensityDiff(top.getColor(), closestOrigPix.getColor());
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
									maskPriorityQueue2.add(top);
								}
							}
						}
						// the one on bottom
						if (temp.getY() < (h * resizeScale - 1)) {
							Pixel bottom = sourcePixArray2[temp.getY() + 1][temp
							                                                .getX()];
							if (!bottom.isUsedByCurrentMask()) { 

								double rangeDistance = intensityDiff(bottom.getColor(), closestOrigPix.getColor());
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
									maskPriorityQueue2.add(bottom);
								}
							}
						}

						pixStorage2.add(temp);
						temp.setUsedByCurrentMask(true);
						origPixCount++;
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
						// if(jcb.isSelected()){
						// biFiltered.setRGB(tempPix.getX(), tempPix.getY(),
						// 255); //blue = 255
						// }
					}
					while (!maskPriorityQueue2.isEmpty()) {
						Pixel temp = maskPriorityQueue2.poll();
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
						i = (int) (h * resizeScale * 2);
						j = (int) (w * resizeScale * 2);
					}
				}
				System.out.println("i" + i);
			}
			break;
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
						// the one on the bottom
						if (temp.getX() > 0) {
							Pixel bottom = sourcePixArray[temp.getY()][temp
							                                           .getX() - 1];

							if (!bottom.isUsedByCurrentMask()) { // dr*dr +
								// dg*dg +
								// db*db
								double colorDistance = (bottom.getColor()
										.getRed() - sourcePixArray[i][j]
												.getColor().getRed())
												* (bottom.getColor().getRed() - sourcePixArray[i][j]
														.getColor().getRed())
														+ (bottom.getColor().getGreen() - sourcePixArray[i][j]
																.getColor().getGreen())
																* (bottom.getColor().getGreen() - sourcePixArray[i][j]
																		.getColor().getGreen())
																		+ (bottom.getColor().getBlue() - sourcePixArray[i][j]
																				.getColor().getBlue())
																				* (bottom.getColor().getBlue() - sourcePixArray[i][j]
																						.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
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

						// the one on the right
						if (temp.getX() < (w - 1)) {
							Pixel right = sourcePixArray[temp.getY()][temp
							                                          .getX() + 1];
							if (!right.isUsedByCurrentMask()) { // dr*dr + dg*dg
								// + db*db
								double colorDistance = (right.getColor()
										.getRed() - sourcePixArray[i][j]
												.getColor().getRed())
												* (right.getColor().getRed() - sourcePixArray[i][j]
														.getColor().getRed())
														+ (right.getColor().getGreen() - sourcePixArray[i][j]
																.getColor().getGreen())
																* (right.getColor().getGreen() - sourcePixArray[i][j]
																		.getColor().getGreen())
																		+ (right.getColor().getBlue() - sourcePixArray[i][j]
																				.getColor().getBlue())
																				* (right.getColor().getBlue() - sourcePixArray[i][j]
																						.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
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
								double colorDistance = (top.getColor().getRed() - sourcePixArray[i][j]
										.getColor().getRed())
										* (top.getColor().getRed() - sourcePixArray[i][j]
												.getColor().getRed())
												+ (top.getColor().getGreen() - sourcePixArray[i][j]
														.getColor().getGreen())
														* (top.getColor().getGreen() - sourcePixArray[i][j]
																.getColor().getGreen())
																+ (top.getColor().getBlue() - sourcePixArray[i][j]
																		.getColor().getBlue())
																		* (top.getColor().getBlue() - sourcePixArray[i][j]
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
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
								double colorDistance = (bottom.getColor()
										.getRed() - sourcePixArray[i][j]
												.getColor().getRed())
												* (bottom.getColor().getRed() - sourcePixArray[i][j]
														.getColor().getRed())
														+ (bottom.getColor().getGreen() - sourcePixArray[i][j]
																.getColor().getGreen())
																* (bottom.getColor().getGreen() - sourcePixArray[i][j]
																		.getColor().getGreen())
																		+ (bottom.getColor().getBlue() - sourcePixArray[i][j]
																				.getColor().getBlue())
																				* (bottom.getColor().getBlue() - sourcePixArray[i][j]
																						.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
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

		case 5: /* upsample+geodesic/w */{
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			// bi = biFiltered;
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray2 = convert2DIntArrayToPixelArray(intPixArray2);
			Pixel[][] resultPixArray2 = new Pixel[h][w];
			LinkedList<Pixel> pixStorage2 = new LinkedList<Pixel>();
			PriorityQueue<Pixel> maskPriorityQueue2 = new PriorityQueue<Pixel>();
			for (int i = 0; i < h * resizeScale; i++) {
				for (int j = 0; j < w * resizeScale; j++) {

					if (jcb.isSelected()) {
						if (mouseX >= w * resizeScale
								|| mouseY >= h * resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale + 1);
							j = (int) (w * resizeScale + 1);
							continue;
						}

						else {
							j = mouseX;
							i = mouseY;
						}

					}

					// clean up storage & priority queue
					pixStorage2.clear();
					maskPriorityQueue2.clear();

					/*
					 * //reset the weight of all of the pixels for(int m=0; m<h;
					 * m++) for(int n=0; n<w; n++)
					 * sourcePixArray[m][n].setWeight(999999999);
					 */

					// start populating the mask
					sourcePixArray2[i][j].setWeight(0);
					maskPriorityQueue2.add(sourcePixArray2[i][j]);
					int origPixCount = 0;
					while (origPixCount < maskSize) {
						// get the lowest weighted pixel from the priority queue
						Pixel temp = maskPriorityQueue2.remove();

						// find the four pixels adjacent to the selected pixel
						// the one on the left
						if (temp.getX() > 0) {
							Pixel left = sourcePixArray2[temp.getY()][temp
							                                          .getX() - 1];

							if (!left.isUsedByCurrentMask()) { // dr*dr +
								// dg*dg +
								// db*db
								double colorDistance = (left.getColor()
										.getRed() - temp.getColor().getRed())
										* (left.getColor().getRed() - temp
												.getColor().getRed())
												+ (left.getColor().getGreen() - temp
														.getColor().getGreen())
														* (left.getColor().getGreen() - temp
																.getColor().getGreen())
																+ (left.getColor().getBlue() - temp
																		.getColor().getBlue())
																		* (left.getColor().getBlue() - temp
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (left.isUsedByCurrentPQ()) {
									if (colorDistance < left.getWeight())
										left.setWeight((int) colorDistance);
									else
										;
								} else {
									left.setWeight((int) colorDistance);
									left.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(left);
								}
							}
						}

						// the one on the right
						if (temp.getX() < (w * resizeScale - 1)) {
							Pixel right = sourcePixArray2[temp.getY()][temp
							                                           .getX() + 1];
							if (!right.isUsedByCurrentMask()) { // dr*dr + dg*dg
								// + db*db
								double colorDistance = (right.getColor()
										.getRed() - temp.getColor().getRed())
										* (right.getColor().getRed() - temp
												.getColor().getRed())
												+ (right.getColor().getGreen() - temp
														.getColor().getGreen())
														* (right.getColor().getGreen() - temp
																.getColor().getGreen())
																+ (right.getColor().getBlue() - temp
																		.getColor().getBlue())
																		* (right.getColor().getBlue() - temp
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (right.isUsedByCurrentPQ()) {
									if (colorDistance < right.getWeight())
										right.setWeight((int) colorDistance);
									else
										;
								} else {
									right.setWeight((int) colorDistance);
									right.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(right);
								}
							}
						}
						// the one on top
						if (temp.getY() > 0) {
							Pixel top = sourcePixArray2[temp.getY() - 1][temp
							                                             .getX()];
							if (!top.isUsedByCurrentMask()) { // dr*dr + dg*dg +
								// db*db
								double colorDistance = (top.getColor().getRed() - temp
										.getColor().getRed())
										* (top.getColor().getRed() - temp
												.getColor().getRed())
												+ (top.getColor().getGreen() - temp
														.getColor().getGreen())
														* (top.getColor().getGreen() - temp
																.getColor().getGreen())
																+ (top.getColor().getBlue() - temp
																		.getColor().getBlue())
																		* (top.getColor().getBlue() - temp
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (top.isUsedByCurrentPQ()) {
									if (colorDistance < top.getWeight())
										top.setWeight((int) colorDistance);
									else
										;
								} else {
									top.setWeight((int) colorDistance);
									top.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(top);
								}
							}
						}

						// the one on bottom
						if (temp.getY() < (h * resizeScale - 1)) {
							Pixel bottom = sourcePixArray2[temp.getY() + 1][temp
							                                                .getX()];
							if (!bottom.isUsedByCurrentMask()) { // dr*dr +
								// dg*dg +
								// db*db
								double colorDistance = (bottom.getColor()
										.getRed() - temp.getColor().getRed())
										* (bottom.getColor().getRed() - temp
												.getColor().getRed())
												+ (bottom.getColor().getGreen() - temp
														.getColor().getGreen())
														* (bottom.getColor().getGreen() - temp
																.getColor().getGreen())
																+ (bottom.getColor().getBlue() - temp
																		.getColor().getBlue())
																		* (bottom.getColor().getBlue() - temp
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (bottom.isUsedByCurrentPQ()) {
									if (colorDistance < bottom.getWeight())
										bottom.setWeight((int) colorDistance);
									else
										;
								} else {
									bottom.setWeight((int) colorDistance);
									bottom.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(bottom);
								}
							}
						}

						pixStorage2.add(temp);
						temp.setUsedByCurrentMask(true);
						origPixCount++;
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
						// if(jcb.isSelected()){
						// biFiltered.setRGB(tempPix.getX(), tempPix.getY(),
						// 255); //blue = 255
						// }
					}
					while (!maskPriorityQueue2.isEmpty()) {
						Pixel temp = maskPriorityQueue2.poll();
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
						i = (int) (h * resizeScale * 2);
						j = (int) (w * resizeScale * 2);
					}
				}
				System.out.println("i" + i);
			}
			break;
		}

		case 6: /* upsample+geodesic/c */{
			w = bi.getWidth(null);
			h = bi.getHeight(null);
			biFiltered = resize(bi, (int) (w * resizeScale),
					(int) (h * resizeScale),
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			// bi = biFiltered;
			int[][] intPixArray2 = convertTo2DUsingGetRGB(biFiltered);
			Pixel[][] sourcePixArray2 = convert2DIntArrayToPixelArray(intPixArray2);
			Pixel[][] resultPixArray2 = new Pixel[h][w];
			LinkedList<Pixel> pixStorage2 = new LinkedList<Pixel>();
			PriorityQueue<Pixel> maskPriorityQueue2 = new PriorityQueue<Pixel>();
			for (int i = 0; i < h * resizeScale; i++) {
				for (int j = 0; j < w * resizeScale; j++) {

					if (jcb.isSelected()) {
						if (mouseX >= w * resizeScale
								|| mouseY >= h * resizeScale) {
							jtf.setText("mouse location exceed image boundaries");
							i = (int) (h * resizeScale + 1);
							j = (int) (w * resizeScale + 1);
							continue;
						}

						else {
							j = mouseX;
							i = mouseY;
						}

					}

					// clean up storage & priority queue
					pixStorage2.clear();
					maskPriorityQueue2.clear();

					/*
					 * //reset the weight of all of the pixels for(int m=0; m<h;
					 * m++) for(int n=0; n<w; n++)
					 * sourcePixArray[m][n].setWeight(999999999);
					 */

					// start populating the mask
					sourcePixArray2[i][j].setWeight(0);
					maskPriorityQueue2.add(sourcePixArray2[i][j]);
					int origPixCount = 0;
					while (origPixCount < maskSize) {
						// get the lowest weighted pixel from the priority queue
						Pixel temp = maskPriorityQueue2.remove();

						// find the four pixels adjacent to the selected pixel
						// the one on the left
						if (temp.getX() > 0) {
							Pixel left = sourcePixArray2[temp.getY()][temp
							                                          .getX() - 1];

							if (!left.isUsedByCurrentMask()) { // dr*dr +
								// dg*dg +
								// db*db
								double colorDistance = (left.getColor()
										.getRed() - sourcePixArray2[i][j].getColor().getRed())
										* (left.getColor().getRed() - sourcePixArray2[i][j]
												.getColor().getRed())
												+ (left.getColor().getGreen() - sourcePixArray2[i][j]
														.getColor().getGreen())
														* (left.getColor().getGreen() - sourcePixArray2[i][j]
																.getColor().getGreen())
																+ (left.getColor().getBlue() - sourcePixArray2[i][j]
																		.getColor().getBlue())
																		* (left.getColor().getBlue() - sourcePixArray2[i][j]
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (left.isUsedByCurrentPQ()) {
									if (colorDistance < left.getWeight())
										left.setWeight((int) colorDistance);
									else
										;
								} else {
									left.setWeight((int) colorDistance);
									left.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(left);
								}
							}
						}

						// the one on the right
						if (temp.getX() < (w * resizeScale - 1)) {
							Pixel right = sourcePixArray2[temp.getY()][temp
							                                           .getX() + 1];
							if (!right.isUsedByCurrentMask()) { // dr*dr + dg*dg
								// + db*db
								double colorDistance = (right.getColor()
										.getRed() - sourcePixArray2[i][j].getColor().getRed())
										* (right.getColor().getRed() - sourcePixArray2[i][j]
												.getColor().getRed())
												+ (right.getColor().getGreen() - sourcePixArray2[i][j]
														.getColor().getGreen())
														* (right.getColor().getGreen() - sourcePixArray2[i][j]
																.getColor().getGreen())
																+ (right.getColor().getBlue() - sourcePixArray2[i][j]
																		.getColor().getBlue())
																		* (right.getColor().getBlue() - sourcePixArray2[i][j]
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (right.isUsedByCurrentPQ()) {
									if (colorDistance < right.getWeight())
										right.setWeight((int) colorDistance);
									else
										;
								} else {
									right.setWeight((int) colorDistance);
									right.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(right);
								}
							}
						}
						// the one on top
						if (temp.getY() > 0) {
							Pixel top = sourcePixArray2[temp.getY() - 1][temp
							                                             .getX()];
							if (!top.isUsedByCurrentMask()) { // dr*dr + dg*dg +
								// db*db
								double colorDistance = (top.getColor().getRed() - sourcePixArray2[i][j]
										.getColor().getRed())
										* (top.getColor().getRed() - sourcePixArray2[i][j]
												.getColor().getRed())
												+ (top.getColor().getGreen() - sourcePixArray2[i][j]
														.getColor().getGreen())
														* (top.getColor().getGreen() - sourcePixArray2[i][j]
																.getColor().getGreen())
																+ (top.getColor().getBlue() - sourcePixArray2[i][j]
																		.getColor().getBlue())
																		* (top.getColor().getBlue() - sourcePixArray2[i][j]
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (top.isUsedByCurrentPQ()) {
									if (colorDistance < top.getWeight())
										top.setWeight((int) colorDistance);
									else
										;
								} else {
									top.setWeight((int) colorDistance);
									top.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(top);
								}
							}
						}

						// the one on bottom
						if (temp.getY() < (h * resizeScale - 1)) {
							Pixel bottom = sourcePixArray2[temp.getY() + 1][temp
							                                                .getX()];
							if (!bottom.isUsedByCurrentMask()) { // dr*dr +
								// dg*dg +
								// db*db
								double colorDistance = (bottom.getColor()
										.getRed() - sourcePixArray2[i][j].getColor().getRed())
										* (bottom.getColor().getRed() - sourcePixArray2[i][j]
												.getColor().getRed())
												+ (bottom.getColor().getGreen() - sourcePixArray2[i][j]
														.getColor().getGreen())
														* (bottom.getColor().getGreen() - sourcePixArray2[i][j]
																.getColor().getGreen())
																+ (bottom.getColor().getBlue() - sourcePixArray2[i][j]
																		.getColor().getBlue())
																		* (bottom.getColor().getBlue() - sourcePixArray2[i][j]
																				.getColor().getBlue());
								colorDistance = temp.getWeight() + R
										* colorDistance;
								if (bottom.isUsedByCurrentPQ()) {
									if (colorDistance < bottom.getWeight())
										bottom.setWeight((int) colorDistance);
									else
										;
								} else {
									bottom.setWeight((int) colorDistance);
									bottom.setUsedByCurrentPQ(true);
									maskPriorityQueue2.add(bottom);
								}
							}
						}

						pixStorage2.add(temp);
						temp.setUsedByCurrentMask(true);
						origPixCount++;
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
						// if(jcb.isSelected()){
						// biFiltered.setRGB(tempPix.getX(), tempPix.getY(),
						// 255); //blue = 255
						// }
					}
					while (!maskPriorityQueue2.isEmpty()) {
						Pixel temp = maskPriorityQueue2.poll();
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
						i = (int) (h * resizeScale * 2);
						j = (int) (w * resizeScale * 2);
					}
				}
				System.out.println("i" + i);
			}
			break;
		}

		}//end of switch
	}

	private static Pixel[][] convert2DIntArrayToPixelArray(int[][] intPixArray) {
		int h = intPixArray.length;
		int w = intPixArray[0].length;
		Pixel[][] pixelArray = new Pixel[h][w];
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				pixelArray[i][j] = new Pixel(j, i,
						new Color(intPixArray[i][j]), 99999999);
			}
		}
		return pixelArray;
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
		final GeodesicFilterV2 si = new GeodesicFilterV2();
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