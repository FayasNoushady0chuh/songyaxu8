package com.cookeem.chat.mongo

import java.util.Date

import com.cookeem.chat.common.CommonUtils._
import com.cookeem.chat.jwt.JwtOps._
import com.cookeem.chat.mongo.MongoOps._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.Future
/**
  * Created by cookeem on 16/10/28.
  */
object MongoLogic {
  val colUsersName = "users"
  val colSessionsName = "sessions"
  val colMessagesName = "messages"
  val colOnlinesName = "onlines"

  val usersCollection = cookimDB.map(_.collection[BSONCollection](colUsersName))
  val sessionsCollection = cookimDB.map(_.collection[BSONCollection](colSessionsName))
  val messagesCollection = cookimDB.map(_.collection[BSONCollection](colMessagesName))
  val onlinesCollection = cookimDB.map(_.collection[BSONCollection](colOnlinesName))

  implicit def sessionStatusHandler = Macros.handler[SessionStatus]
  implicit def userHandler = Macros.handler[User]
  implicit def userStatusHandler = Macros.handler[UserStatus]
  implicit def sessionHandler = Macros.handler[Session]
  implicit def fileInfoHandler = Macros.handler[FileInfo]
  implicit def messageHandler = Macros.handler[Message]
  implicit def onlineHandler = Macros.handler[Online]

  //create users collection and index
  def createUsersCollection(): Future[String] = {
    val indexSettings = Array(
      ("login", 1, true, 0)
    )
    createIndex(colUsersName, indexSettings)
  }

  //create sessions collection and index
  def createSessionsCollection(): Future[String] = {
    val indexSettings = Array(
      ("senduid", 1, false, 0),
      ("recvuid", 1, false, 0)
    )
    createIndex(colSessionsName, indexSettings)
  }

  //create messages collection and index
  def createMessagesCollection(): Future[String] = {
    val indexSettings = Array(
      ("senduid", 1, false, 0),
      ("sessionid", 1, false, 0)
    )
    createIndex(colMessagesName, indexSettings)
  }

  //create online collection and index
  def createOnlinesCollection(): Future[String] = {
    val indexSettings = Array(
      ("uid", 1, true, 0),
      ("dateline", -1, false, 15 * 60)
    )
    createIndex(colOnlinesName, indexSettings)
  }

  //register new user
  def registerUser(login: String, nickname: String, password: String, gender: Int, avatar: String): Future[(String, String, String)] = {
    var errmsg = ""
    val token = ""
    if (!isEmail(login)) {
      errmsg = "login must be email"
    } else if (nickname.getBytes.length < 4) {
      errmsg = "nickname must at least 4 charactors"
    } else if (password.length < 6) {
      errmsg = "password must at least 6 charactors"
    } else if (!(gender == 1 || gender == 2)) {
      errmsg = "gender must be boy or girl"
    } else if (avatar.length < 6) {
      errmsg = "avatar must at least 6 charactors"
    }
    if (errmsg != "") {
      Future(("", token, errmsg))
    } else {
      for {
        user <- findCollectionOne[User](usersCollection, document("login" -> login))
        (uid, token, errmsg) <- {
          if (user != null) {
            errmsg = "user already exist"
            Future((user._id, token, errmsg))
          } else {
            val newUser = User("", login, nickname, sha1(password), gender, avatar)
            insertCollection[User](usersCollection, newUser).map { case (iuid, ierrmsg) =>
              if (iuid != "") {
                loginUpdate(iuid)
                createUserToken(iuid).map { token => (iuid, token, ierrmsg) }
              } else {
                Future((iuid, token, ierrmsg))
              }
            }.flatMap(f => f)
          }
        }
      } yield {
        (uid, token, errmsg)
      }
    }
  }

  def getUserInfo(uid: String): Future[User] = {
    findCollectionOne[User](usersCollection, document("_id" -> uid))
  }

  //update users info
  def updateUserInfo(uid: String, nickname: String = "", gender: Int = 0, avatar: String = ""): Future[UpdateResult] = {
    var errmsg = ""
    var update = document()
    var sets = document()
    if (nickname.getBytes.length >= 4) {
      sets = sets.merge(document("nickname" -> nickname))
    }
    if (gender == 1 || gender == 2) {
      sets = sets.merge(document("gender" -> gender))
    }
    if (avatar.startsWith("images/")) {
      val avatarDefault = gender match {
        case 1 => "images/avatar/boy.jpg"
        case 2 => "images/avatar/girl.jpg"
        case _ => "images/avatar/unknown.jpg"
      }
      sets = sets.merge(document("avatar" -> avatarDefault))
    } else if (avatar.length >= 16) {
      sets = sets.merge(document("avatar" -> avatar))
    }
    if (sets == document()) {
      errmsg = "nothing to update"
    } else {
      update = document("$set" -> sets)
    }
    if (errmsg != "") {
      Future(UpdateResult(n = 0, errmsg = errmsg))
    } else {
      updateCollection(usersCollection, document("_id" -> uid), update)
    }
  }

  def loginAction(login: String, pwd: String): Future[(String, String)] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("login" -> login))
      (uid, token) <- {
        var uid = ""
        if (user != null) {
          val pwdSha1 = user.password
          if (pwdSha1 != "" && sha1(pwd) == pwdSha1) {
            uid = user._id
            loginUpdate(uid)
          }
        }
        if (uid != "") {
          createUserToken(uid).map { token => (uid, token) }
        } else {
          Future("", "")
        }
      }
    } yield {
      (uid, token)
    }
  }

  def logoutAction(userTokenStr: String): Future[UpdateResult] = {
    val userToken = verifyUserToken(userTokenStr)
    if (userToken.uid != "") {
      removeCollection(onlinesCollection, document("uid" -> userToken.uid))
    } else {
      Future(UpdateResult(n = 0, errmsg = "no privilege to logout"))
    }
  }

  //update user online status
  def updateOnline(uid: String): Future[String] = {
    val selector = document("uid" -> uid)
    for {
      online <- findCollectionOne[Online](onlinesCollection, selector)
      errmsg <- {
        if (online == null) {
          // time expire after 15 minutes
          val onlineNew = Online("", uid, new Date())
          insertCollection[Online](onlinesCollection, onlineNew).map { case (id, errmsg) =>
            errmsg
          }
        } else {
          val update = document("$set" -> document("dateline" -> new Date()))
          updateCollection(onlinesCollection, selector, update).map { ur =>
            ur.errmsg
          }
        }
      }
    } yield {
      errmsg
    }
  }

  //check and change password
  def changePwd(uid: String, oldPwd: String, newPwd: String, renewPwd: String): Future[UpdateResult] = {
    var errmsg = ""
    if (oldPwd.length < 6) {
      errmsg = "old password must at least 6 charactors"
    } else if (newPwd.length < 6) {
      errmsg = "new password must at least 6 charactors"
    } else if (newPwd != renewPwd) {
      errmsg = "new password and repeat password must be same"
    } else if (newPwd == oldPwd) {
      errmsg = "new password and old password can not be same"
    }
    if (errmsg != "") {
      Future(UpdateResult(0, errmsg))
    } else {
      val selector = document("_id" -> uid, "password" -> sha1(oldPwd))
      val update = document(
        "$set" -> document("password" -> sha1(newPwd))
      )
      updateCollection(usersCollection, selector, update).map{ ur =>
        if (ur.n == 0) {
          errmsg = "user not exist or password not match"
          UpdateResult(0, errmsg)
        } else {
          ur
        }
      }
    }
  }

  //when user login, update the logincount and lastlogin and online info
  def loginUpdate(uid: String): Future[UpdateResult] = {
    for {
      onlineResult <- updateOnline(uid)
      loginResult <- {
        val selector = document("_id" -> uid)
        val update = document(
          "$inc" -> document("logincount" -> 1),
          "$set" -> document("lastlogin" -> System.currentTimeMillis())
        )
        updateCollection(usersCollection, selector, update)
      }
    } yield {
      loginResult
    }
  }

  //join new friend
  def joinFriend(uid: String, fuid: String): Future[UpdateResult] = {
    var errmsg = ""
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid, "friends" -> document("$ne" -> fuid)))
      friend <- findCollectionOne[User](usersCollection, document("_id" -> fuid))
      updateResult <- {
        if (user == null) {
          errmsg = "user not exist or already your friend"
        }
        if (friend == null) {
          errmsg = "user friend not exists"
        }
        var ret = Future(UpdateResult(n = 0, errmsg = errmsg))
        if (errmsg == "") {
          val update = document("$push" -> document("friends" -> fuid))
          ret = updateCollection(usersCollection, document("_id" -> uid), update)
        }
        ret
      }
    } yield {
      updateResult
    }
  }

  //remove friend
  def removeFriend(uid: String, fuid: String): Future[UpdateResult] = {
    var errmsg = ""
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid, "friends" -> document("$eq" -> fuid)))
      ret <- {
        if (user == null) {
          errmsg = "user not exists or friend not in your friends"
          Future(UpdateResult(n = 0, errmsg = errmsg))
        } else {
          val update = document("$pull" -> document("friends" -> fuid))
          updateCollection(usersCollection, document("_id" -> uid), update)
        }
      }
    } yield {
      ret
    }
  }

  def listFriends(uid: String, page: Int = 1, count: Int = 10): Future[List[User]] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid))
      friends <- {
        var friends = Future(List[User]())
        if (user != null) {
          val fuids = user.friends
          val selector = document(
            "_id" -> document(
              "$in" -> fuids
            )
          )
          friends = findCollection[User](usersCollection, selector, page = page, count = count)
        }
        friends
      }
    } yield {
      friends
    }
  }

  //create a new group session
  def createGroupSession(uid: String, chaticon: String, publictype: Int, name: String): Future[(String, String)] = {
    var errmsg = ""
    val selector = document("_id" -> uid)
    val sessiontype = 1
    for {
      user <- findCollectionOne[User](usersCollection, selector)
      (sessionid, errmsg) <- {
        if (user == null) {
          errmsg = "user not exists"
          Future("", errmsg)
        } else if (name.length < 3) {
          errmsg = "session desc must at least 3 character"
          Future("", errmsg)
        } else if (!(publictype == 0 || publictype == 1)) {
          errmsg = "publictype error"
          Future("", errmsg)
        } else if (chaticon.length < 6) {
          errmsg = "please select chat icon"
          Future("", errmsg)
        } else {
          val newSession = Session("", senduid = uid, recvuid = "", chaticon, sessiontype, publictype, name)
          val insRet = insertCollection[Session](sessionsCollection, newSession)
          for {
            (sessionid, errormsg) <- insRet
            retJoin <- {
              var retJoin = Future(UpdateResult(n = 0, errmsg = errormsg))
              if (errormsg == "") {
                retJoin = joinSession(uid, sessionid)
              }
              retJoin
            }
          } yield {
            retJoin
          }
          insRet
        }
      }
    } yield {
      (sessionid, errmsg)
    }
  }

  //create private session if not exist or get private session
  def createPrivateSession(uid: String, ouid: String): Future[(String, String)] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid))
      ouser <- findCollectionOne[User](usersCollection, document("_id" -> ouid))
      (session, errmsgUserNotExist) <- {
        var errmsg = ""
        var ret = Future[(Session, String)](null, errmsg)
        if (user != null && ouser != null) {
          val selector = document(
            "$or" -> array(
              document("senduid" -> uid, "recvuid" -> ouid),
              document("senduid" -> ouid, "recvuid" -> uid)
            )
          )
          ret = findCollectionOne[Session](sessionsCollection, selector).map {s => (s, "")}
        } else {
          errmsg = "send user or recv user not exist"
          ret = Future(null, errmsg)
        }
        ret
      }
      (sessionid, errmsg) <- {
        var ret = Future("", errmsgUserNotExist)
        if (errmsgUserNotExist == "") {
          if (session != null) {
            ret = Future(session._id, "")
          } else {
            val newSession = Session("", senduid = uid, recvuid = "", chaticon = "", sessiontype = 0, publictype = 0, name = "")
            ret = insertCollection[Session](sessionsCollection, newSession)
            for {
              (sessionid, errmsg) <- ret
              uidJoin <- {
                if (sessionid != "") {
                  joinSession(uid, sessionid)
                } else {
                  Future(UpdateResult(0, "sessionid is empty"))
                }
              }
              ouidJoin <- {
                if (sessionid != "") {
                  joinSession(uid, sessionid)
                } else {
                  Future(UpdateResult(0, "sessionid is empty"))
                }
              }
            } yield {
            }
          }
        }
        ret
      }
    } yield {
      (sessionid, errmsg)
    }
  }

  //get session info and users who join this session
  def getSessionInfo(sessionid: String): Future[(Session, List[User])] = {
    for {
      session <- findCollectionOne[Session](sessionsCollection, document("_id" -> sessionid))
      users <- {
        var users = Future(List[User]())
        if (session != null) {
          val uids = session.usersstatus.map(_.uid)
          users = findCollection[User](usersCollection, document("_id" -> document("$in" -> array(uids))))
        }
        users
      }
    } yield {
      (session, users)
    }
  }

  //update session info
  def updateSessionInfo(sessionid: String, uid: String, publictype: Int, name: String): Future[UpdateResult] = {
    var errmsg = ""
    var update = document()
    if (!(publictype == 0 || publictype == 1)) {
      errmsg = "publictype error"
    } else if (name == "") {
      errmsg = "name can not be empty"
    } else {
      update = document(
        "$set" -> document(
          "publictype" -> publictype,
          "name" -> name
        )
      )
    }
    var ret = Future(UpdateResult(n = 0, errmsg = errmsg))
    if (errmsg == "") {
      ret = for {
        session <- findCollectionOne[Session](sessionsCollection, document("_id" -> sessionid, "senduid" -> uid))
        updateResult <- {
          if (session == null) {
            Future(UpdateResult(n = 0, errmsg = "no privilege to update session info"))
          } else {
            updateCollection(sessionsCollection, document("_id" -> sessionid), update)
          }
        }
      } yield {
        updateResult
      }
      Future(UpdateResult(n = 0, errmsg = errmsg))
    }
    ret
  }


  //join new session
  def joinSession(uid: String, sessionid: String): Future[UpdateResult] = {
    var errmsg = ""
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid, "sessionsstatus.sessionid" -> document("$ne" -> sessionid)))
      session <- findCollectionOne[Session](sessionsCollection, document("_id" -> sessionid))
      updateResult <- {
        if (user == null) {
          errmsg = "user not exists or already join session"
        }
        if (session == null) {
          errmsg = "session not exists"
        }
        var ret = Future(UpdateResult(n = 0, errmsg = errmsg))
        if (errmsg == "") {
          ret = for {
            ur1 <- {
              val docSessionStatus = document("sessionid" -> sessionid, "newcount" -> 0)
              val update1 = document("$push" -> document("sessionsstatus" -> docSessionStatus))
              updateCollection(usersCollection, document("_id" -> uid), update1)
            }
            ur2 <- {
              val docUserStatus = document("uid" -> uid, "online" -> false)
              val update2 = document("$push" -> document("usersstatus" -> docUserStatus))
              updateCollection(sessionsCollection, document("_id" -> sessionid), update2)
            }
          } yield {
            ur2
          }
        }
        ret
      }
    } yield {
      updateResult
    }
  }

  //leave session
  def leaveSession(uid: String, sessionid: String): Future[UpdateResult] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid, "sessionsstatus.sessionid" -> sessionid))
      session <- findCollectionOne[Session](sessionsCollection, document("_id" -> sessionid, "usersstatus.uid" -> uid))
      ret <- {
        if (user == null || session == null) {
          val errmsg = "user not exists or not join the session"
          Future(UpdateResult(n = 0, errmsg = errmsg))
        } else {
          for {
            ur1 <- {
              val sessionstatus = user.sessionsstatus.filter(_.sessionid == sessionid).head
              val docSessionStatus = document("sessionid" -> sessionstatus.sessionid, "newcount" -> sessionstatus.newcount)
              val update1 = document("$pull" -> document("sessionsstatus" -> docSessionStatus))
              updateCollection(usersCollection, document("_id" -> uid), update1)
            }
            ur2 <- {
              val userstatus = session.usersstatus.filter(_.uid == uid).head
              val docUserStatus = document("uid" -> userstatus.uid, "online" -> userstatus.online)
              val update2 = document("$pull" -> document("usersstatus" -> docUserStatus))
              updateCollection(sessionsCollection, document("_id" -> sessionid), update2)
            }
          } yield {
            ur2
          }
        }
      }
    } yield {
      ret
    }
  }

  //list all session, showType: (0: private, 1:group , 2:all)
  def listSessions(uid: String, isPublic: Boolean, showType: Int = 2, page: Int = 1, count: Int = 10): Future[List[Session]] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid))
      sessions <- {
        var sessions = Future(List[Session]())
        if (user != null) {
          val sessionids = user.sessionsstatus.map(_.sessionid)
          var ba = array()
          sessionids.foreach { sessionid =>
            ba = ba.merge(sessionid)
          }
          var selector = document()
          if (isPublic) {
            selector = document(
              "publictype" -> 1,
              "sessiontype" -> 1,
              "_id" -> document(
                "$nin" -> ba
              )
            )
          } else {
            var selector = document(
              "_id" -> document(
                "$in" -> ba
              )
            )
            showType match {
              case 0 =>
                selector = selector.merge(document("sessiontype" -> 0))
              case 1 =>
                selector = selector.merge(document("sessiontype" -> 1))
              case _ =>
            }
          }
          if (ba.size > 0) {
            sessions = findCollection[Session](sessionsCollection, selector, sort = document("dateline" -> -1), page = page, count = count)
          }
        }
        sessions
      }
    } yield {
      sessions
    }
  }

  //verify user is in session
  def verifySession(senduid: String, sessionid: String): Future[String] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> senduid, "sessionsstatus.sessionid" -> sessionid))
      session <- findCollectionOne[Session](sessionsCollection, document("_id" -> sessionid, "usersstatus.uid" -> senduid))
    } yield {
      if (user != null && session != null) {
        ""
      } else {
        "no privilege in this session"
      }
    }
  }

  //create a new message
  def createMessage(userTokenStr: String, sessionTokenStr: String, msgtype: String, noticetype: String, message: String, fileinfo: FileInfo): Future[(String, String)] = {
    for {
      (msgid, errmsg) <- {
        var errmsg = ""
        val userSessionInfo = verifyUserSessionToken(userTokenStr, sessionTokenStr)
        if (userSessionInfo.uid == "") {
          errmsg = "no privilege to send message"
          Future(("", errmsg))
        } else {
          val uid = userSessionInfo.uid
          val sessionid = userSessionInfo.sessionid
          val newMessage = Message("", uid, sessionid, msgtype, noticetype = noticetype, message = message, fileinfo = fileinfo)
          insertCollection[Message](messagesCollection, newMessage)
        }
      }
    } yield {
      (msgid, errmsg)
    }
  }

  def getMessageById(userTokenStr: String, sessionTokenStr: String, msgid: String): Future[(Message, User)] = {
    val UserSessionInfo(uid, nickname, avatar, sessionid) = verifyUserSessionToken(userTokenStr, sessionTokenStr)
    if (uid == "") {
      Future((null, null))
    } else {
      for {
        message <- findCollectionOne[Message](messagesCollection, document("_id" -> msgid, "sessionid" -> sessionid))
        (message, user) <- {
          if (message != null) {
            findCollectionOne[User](usersCollection, document("_id" -> message.senduid)).map { user =>
              (message, user)
            }
          } else {
            Future((message, null))
          }
        }
      } yield {
        (message, user)
      }
    }
  }

  def getSessionLastMessage(userTokenStr: String, sessionid: String): Future[(Session, Message, User)] = {
    val UserToken(uid, nickname, avatar) = verifyUserToken(userTokenStr)
    if (uid != "") {
      for {
        session <- findCollectionOne[Session](sessionsCollection, document("_id" -> sessionid))
        message <- {
          if (session != null) {
            findCollectionOne[Message](messagesCollection, document("_id" -> session.lastmsgid))
          } else {
            null
          }
        }
        user <- {
          if (message != null) {
            findCollectionOne[User](usersCollection, document("_id" -> message.senduid))
          } else {
            Future(null)
          }
        }
      } yield {
        (session, message, user)
      }
    } else {
      Future(null, null, null)
    }
  }

  //list history messages
  def listHistoryMessages(uid: String, sessionid: String, page: Int = 1, count: Int = 10, sort: BSONDocument): Future[(String, List[(Message, User)])] = {
    for {
      errmsg <- verifySession(uid, sessionid)
      messages <- {
        var messages = Future(List[Message]())
        if (errmsg == "") {
          messages = findCollection[Message](messagesCollection, document("sessionid" -> sessionid), sort = sort, page = page, count = count)
        }
        messages
      }
      listMessageUser <- {
        Future.sequence(
          messages.map { message =>
            findCollectionOne[User](usersCollection, document("_id" -> message.senduid)).map { user =>
              (message, user)
            }
          }
        )
      }
    } yield {
      (errmsg, listMessageUser)
    }
  }

  //create user token, include uid, nickname, avatar
  def createUserToken(uid: String): Future[String] = {
    for {
      user <- findCollectionOne[User](usersCollection, document("_id" -> uid))
    } yield {
      var token = ""
      if (user != null) {
        val payload = Map[String, Any](
          "uid" -> user._id,
          "nickname" -> user.nickname,
          "avatar" -> user.avatar
        )
        token = encodeJwt(payload)
      }
      token
    }
  }

  def verifyUserToken(token: String): UserToken = {
    var userToken = UserToken("", "", "")
    val mapUserToken = decodeJwt(token)
    if (mapUserToken.contains("uid") && mapUserToken.contains("nickname") && mapUserToken.contains("avatar")) {
      val uid = mapUserToken("uid").asInstanceOf[String]
      val nickname = mapUserToken("nickname").asInstanceOf[String]
      val avatar = mapUserToken("avatar").asInstanceOf[String]
      if (uid != "" && nickname != "" && avatar != "") {
        userToken = UserToken(uid, nickname, avatar)
      }
    }
    userToken
  }

  //create session token, include sessionid
  def createSessionToken(uid: String, sessionid: String): Future[String] = {
    for {
      errmsg <- verifySession(uid, sessionid)
    } yield {
      var token = ""
      if (errmsg == "") {
        val payload = Map[String, Any](
          "sessionid" -> sessionid
        )
        token = encodeJwt(payload)
      }
      token
    }
  }

  def verifySessionToken(token: String): SessionToken = {
    var sessionToken = SessionToken("")
    val mapSessionToken = decodeJwt(token)
    if (mapSessionToken.contains("sessionid")) {
      val sessionid = mapSessionToken("sessionid").asInstanceOf[String]
      if (sessionid != "") {
        sessionToken = SessionToken(sessionid)
      }
    }
    sessionToken
  }

  def verifyUserSessionToken(userTokenStr: String, sessionTokenStr: String): UserSessionInfo = {
    val userToken = verifyUserToken(userTokenStr)
    val sessionToken = verifySessionToken(sessionTokenStr)
    if (userToken.uid != "" && userToken.nickname != "" && userToken.avatar != "" && sessionToken.sessionid != "") {
      UserSessionInfo(userToken.uid, userToken.nickname, userToken.avatar, sessionToken.sessionid)
    } else {
      UserSessionInfo("", "", "", "")
    }
  }

}
