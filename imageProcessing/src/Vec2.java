
public class Vec2 {
	double x;
	double y;
	
	public Vec2(double x, double y){
		this.x = x;
		this.y = y;
	}
	
	public static Vec2 normalize(Vec2 v){
		double sqrt = Math.sqrt(v.x*v.x + v.y*v.y);
		if(sqrt == 0)
			return new Vec2(0.0, 0.0);
		else
			return new Vec2(v.x/sqrt, v.y/sqrt);
	}
	
	public double getLength(){
		return Math.sqrt(x*x + y*y);
	}
	
	public static Vec2 Vec2Add (Vec2 v1, Vec2 v2){
		return new Vec2 (v1.x+v2.x, v1.y+v2.y);
	}
	
	public static Vec2 Vec2Sub (Vec2 v1, Vec2 v2){
		return new Vec2 (v1.x-v2.x, v1.y-v2.y);
	}

	public static double angleBetweenTwoVector(Vec2 v1, Vec2 v2) {
		return Math.acos((v1.x*v2.x + v1.y*v2.y)/(v1.getLength()*v2.getLength()));
	}
	
	public void negate(){
		x = -x;
		y = -y;
	}
	
	public static double dot(Vec2 p1, Vec2 p2){
		return p1.x * p2.x + p1.y * p2.y;
	}
	
	public void scale(double factor){
		x*= factor;
		y*= factor;
	}

}
