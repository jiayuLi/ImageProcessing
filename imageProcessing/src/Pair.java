
public class Pair implements Comparable{
	public int a = -1;
	public int b = -1;
	
	public Pair(int x, int y){
		this.a = x;
		this.b = y;
	}

	@Override
	public int compareTo(Object o) {
		Pair p2 = (Pair) o;
		if(this.b<p2.b)
			return 1;
		else if(this.b==p2.b&&this.a<p2.a)
			return 1;
		else if(this.a==p2.a&&this.b==p2.b)
			return 0;
		else
			return -1;
	}
}
