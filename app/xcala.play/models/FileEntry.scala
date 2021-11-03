//package xcala.play.models
//
//import reactivemongo.api.bson._
//import reactivemongo.api.bson.DefaultBSONHandlers
//import reactivemongo.api.gridfs._

// TODO: Fix Soheil
//case class FileEntry(
//	id: BSONObjectID,
//	contentType: Option[String],
//	filename: String,
//	uploadDate: Option[Long],
//	chunkSize: Int,
//	length: Int,
//	md5: Option[String],
//	metadata: BSONDocument,
//	folderId: Option[BSONObjectID],
//	original: BSONDocument
//) extends ReadFile[BSONObjectID, BSONDocument] {
//
//  def isImage = contentType.map(_.startsWith("image/")).getOrElse(false)
//}
//
//object FileEntry {
//  implicit object BSONDocumentReader extends BSONDocumentReader[FileEntry] {
//	  import DefaultBSONHandlers._
//	  def read(doc: BSONDocument) = {
//	    FileEntry(
//	      doc.getAs[BSONObjectID]("_id").get,
//	      doc.getAs[BSONString]("contentType").map(_.value),
//	      doc.getAs[BSONString]("filename").map(_.value).get,
//	      doc.getAs[BSONNumberLike]("uploadDate").map(_.toLong),
//	      doc.getAs[BSONNumberLike]("chunkSize").map(_.toInt).get,
//	      doc.getAs[BSONNumberLike]("length").map(_.toInt).get,
//	      doc.getAs[BSONString]("md5").map(_.value),
//	      doc.getAs[BSONDocument]("metadata").getOrElse(BSONDocument()),
//	      doc.getAs[BSONObjectID]("folderId"),
//	      doc)
//	  }
//	}
//}
//
//object FileType {
//  val Image = "^image.*$"
//  val Video = "^video.*$"
//  val Audio = "^audio.*$"
//}