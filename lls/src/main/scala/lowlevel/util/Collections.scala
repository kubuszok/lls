package lowlevel.util

/** Java's `Collections.allocateIterators` flag. The generated port's iterator pools read this; the original libGDX type is dropped and redirected here by the porting manifest.
  */
object Collections {
  var allocateIterators: Boolean = false
}
