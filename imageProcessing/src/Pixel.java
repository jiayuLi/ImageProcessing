import java.awt.Color;


public class Pixel implements Comparable<Pixel> {

	private int x;
	private int y;
	private Color color;
	private double weight=999999999;
	private boolean usedByCurrentMask = false;
	private boolean usedByCurrentPQ = false;
	public boolean isInPaintPot = false;;//if this pixel is from the paint pot or not
	public double rx;
	public double ry;
	public double deltaH = 0; //horizontal intensity diff
	public double deltaV = 0; //vertical intensity diff
	public double restLengthC1 = 0; //cross rest length (from ij to (i+1)(j+1))
	public double restLengthC2 = 0; //another cross/diagonal rest length (from ij to (i-1)(j+1))
	public double restLengthH = Interpolation.resizeScale;
	public double restLengthV = Interpolation.resizeScale;
	public Vec2 gradient = new Vec2 (0,0);
	public double textureness;
	public double xCumul;
	public double yCumul;
	public double cumulCount;
	public double intensity;
	public double mog;
	public boolean isClusterCenter = false;
	public int clusterIndex;
	public Pair label = new Pair(-1,-1);
	
	public Pixel(){
		
	}
	public boolean isUsedByCurrentPQ() {
		return usedByCurrentPQ;
	}

	public void setUsedByCurrentPQ(boolean usedByCurrentPQ) {
		this.usedByCurrentPQ = usedByCurrentPQ;
	}



	public boolean isUsedByCurrentMask() {
		return usedByCurrentMask;
	}



	public void setUsedByCurrentMask(boolean usedByCurrentMask) {
		this.usedByCurrentMask = usedByCurrentMask;
	}


	public int getX() {
		return x;
	}



	public void setX(int x) {
		this.x = x;
	}



	public int getY() {
		return y;
	}



	public void setY(int y) {
		this.y = y;
	}



	public Color getColor() {
		return color;
	}



	public void setColor(Color color) {
		this.color = color;
	}



	public double getWeight() {
		return weight;
	}



	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	public Vec2 getVec2(){
		return new Vec2(rx, ry);
	}



	public Pixel(int x, int y, Color c, double weight){
		this.x = x;
		this.y = y;
		this.color = c;
		this.weight = weight;
	}

	public double getIntensity(){
		return Math.sqrt(color.getRed()*color.getRed() + color.getBlue()*color.getBlue() + color.getGreen()*color.getGreen())/443.405*256;
	}
	
	public double getHue(){
		double h = Math.atan(1.73205080757*(color.getGreen()-color.getBlue())/(2*color.getRed()-color.getGreen()-color.getBlue()));
		h = h*57.29578; //*360/2/3.1415926
		if (h<-180)
			h = -180;
		if(h>179)
			h = 179;
		return h;
	}
	
	public double getLightness(){
		int M = Math.max(color.getRed(), color.getGreen());
		M = Math.max(M, color.getBlue());
		int m = Math.min(color.getRed(), color.getGreen());
		m = Math.min(m, color.getBlue());
		return 0.5*M+0.5*m;
	}

	@Override
	public int compareTo(Pixel o) {
		if(this.weight>o.weight)
			return 1;
		else if(this.weight<o.weight)
			return -1;
		else
			return 0;
	}
}
