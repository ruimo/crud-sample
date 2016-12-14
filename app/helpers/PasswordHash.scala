package helpers

import java.security.SecureRandom
import scala.util.Random
import javax.inject._
import java.security.MessageDigest
import com.google.common.primitives.Longs

@Singleton
class PasswordHash {
  val saltSource: SecureRandom = new SecureRandom

  def createToken(): Long = saltSource.nextLong

  // (salt, password_hash)
  def generateWithSalt(password: String, stretchCount: Int = 1000): (Long, Long) = {
    val salt = createToken()
    (salt, generate(password, salt, stretchCount))
  }

  def generate(password: String, salt: Long, stretchCount: Int = 1000): Long = {
    val md = createSha1Encoder
    val saltBytes = Longs.toByteArray(salt)
    val passwordBytes = password.getBytes("utf-8")
    for (_ <- 1 to stretchCount) {
      md.update(saltBytes);
      md.update(passwordBytes)
    }
    Longs.fromByteArray(md.digest())
  }

  def createSha1Encoder = MessageDigest.getInstance("SHA-256")

  def password(length: Int = 24): String =
    new Random(saltSource).alphanumeric.take(length).foldLeft(new StringBuilder)(_.append(_)).toString
}
