import java.util.ArrayList;


public class Region implements Comparable{
	int label = -1;
	int size = 0;
	int fixedLabel = -1;
	int numNeighborPixelsBelongToBigRegion = 0;
	ArrayList<Pair> pair = new ArrayList<Pair>();
	
	public Region(){
		
	}
	
	public Region(int label){
		this.label = label;
	}

	@Override
	public int compareTo(Object r2) {
		Region r = (Region)r2;
		if (this.numNeighborPixelsBelongToBigRegion>r.numNeighborPixelsBelongToBigRegion)
			return -1;
		else if(this.numNeighborPixelsBelongToBigRegion<r.numNeighborPixelsBelongToBigRegion)
			return 1;
		else
			return 0;
	}
	
}
