package tech.onetap.util.waypoint;

public class Waypoint {
   private String name;
   private final double x;
   private final double y;
   private final double z;

   public Waypoint(String name, double x, double y, double z) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public String getName() { return this.name; }
   public double getX() { return this.x; }
   public double getY() { return this.y; }
   public double getZ() { return this.z; }
}
