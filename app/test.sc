
def test(o: Option[Int]): Option[Int] = {
  for {
    v <- o if o.isDefined && v >= 10
  } yield v
}

println(test(Some(5)))
println(test(Some(15)))
println(test(None))