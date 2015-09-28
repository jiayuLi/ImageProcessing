import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;
public class BatchProcessing {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		BufferedImage bi, biFiltered;
		int w, h;
		int resizeScale = 6;
		double maskSize = 100;
		int regionWH = (int) Math.sqrt(maskSize*8);
		int histogram[] = new int[256];
		
		for(double modeM = 0.1; modeM<1.0; modeM+=0.2){
			for(double modeK = 1; modeK<10.;modeK+=2){
				bi = ImageIO.read(new File("roughwater_small.png")); 
				int[][] intPixArray = Tools.convertTo2DUsingGetRGB(bi);
				Pixel[][] sourcePixArray = Tools.convert2DIntArrayToPixelArray(intPixArray);
				
				w = bi.getWidth(null);
				h = bi.getHeight(null);
				biFiltered = Tools.resize(bi, (int) (w * resizeScale),
						(int) (h * resizeScale),RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				int[][] intPixArray2 = Tools.convertTo2DUsingGetRGB(biFiltered);
				Pixel[][] upsampledPixArray = Tools.convert2DIntArrayToPixelArray(intPixArray2);
				
				PriorityQueue<Pixel> pixStorage = new PriorityQueue<Pixel>();
				LinkedList<Pixel> maskStorage = new LinkedList<Pixel>();
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
										.setWeight(Tools.fractionalCalculateBilateralDistance(fractionJ, fractionI, upsampledPixArray[i][j].getColor(),sourcePixArray[(int)startingY+n][(int)startingX+m]));
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
							maskStorage.add(temp);//store the mask data
						}
						//smooth out the histogram
						histogram[0] = (int)((preSmoothedHistogram[0]*4 + preSmoothedHistogram[1]*2 + preSmoothedHistogram[2]*1)/7.0);
						histogram[1] = (int)((preSmoothedHistogram[0]*2 + preSmoothedHistogram[1]*4 + preSmoothedHistogram[2]*2 +preSmoothedHistogram[3]*1)/9.0);
						histogram[255] = (int)((preSmoothedHistogram[255]*4 + preSmoothedHistogram[254]*2 + preSmoothedHistogram[253]*1)/7.0);
						histogram[254] = (int)((preSmoothedHistogram[255]*2 + preSmoothedHistogram[254]*4 + preSmoothedHistogram[253]*2 +preSmoothedHistogram[252]*1)/9.0);
						for( int s=2; s<254; s++){
							histogram[s] = (int)((preSmoothedHistogram[s-2]*1+ preSmoothedHistogram[s-1]*2 + preSmoothedHistogram[s]*4 + preSmoothedHistogram[s+1]*2 +preSmoothedHistogram[s+2]*1)/10.0);
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
						double outputIntensity = upsampledPixArray[i][j].getIntensity() + distance* Tools.calculateS(distance, modeM, modeK );
						
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
						
						biFiltered.setRGB(j, i, Tools.getIntFromColor(tempColor));
						
						
						pixStorage.clear();
						
					}
					System.out.println("i" + i );
				}
				String outputS = "batchresult/" + "MaskSize"+maskSize +"_K" + modeK + "_M" + modeM + ".png";
				File output = new File (outputS);
				ImageIO.write(biFiltered, "png", output);
			}
		}
		//File output = new File ("batchresult/output.png");
		//ImageIO.write(input, "png", output);
	}
	
	/*
	{
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
					maskStorage.add(temp);//store the mask data
				}
				//smooth out the histogram
				histogram[0] = (int)((preSmoothedHistogram[0]*4 + preSmoothedHistogram[1]*2 + preSmoothedHistogram[2]*1)/7.0);
				histogram[1] = (int)((preSmoothedHistogram[0]*2 + preSmoothedHistogram[1]*4 + preSmoothedHistogram[2]*2 +preSmoothedHistogram[3]*1)/9.0);
				histogram[255] = (int)((preSmoothedHistogram[255]*4 + preSmoothedHistogram[254]*2 + preSmoothedHistogram[253]*1)/7.0);
				histogram[254] = (int)((preSmoothedHistogram[255]*2 + preSmoothedHistogram[254]*4 + preSmoothedHistogram[253]*2 +preSmoothedHistogram[252]*1)/9.0);
				for( int s=2; s<254; s++){
					histogram[s] = (int)((preSmoothedHistogram[s-2]*1+ preSmoothedHistogram[s-1]*2 + preSmoothedHistogram[s]*4 + preSmoothedHistogram[s+1]*2 +preSmoothedHistogram[s+2]*1)/10.0);
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
	*/
}
