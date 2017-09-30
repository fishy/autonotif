package com.yhsif.autonotif

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.CarExtender
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput

import scala.collection.JavaConversions
import scala.collection.immutable.Set
import scala.collection.mutable.Map

object NotificationListener {
  val ReplyAction = "com.yhsif.autonotif.ACTION_REPLY" // not really used
  val ReplyKey = "com.yhsif.autonorif.KEY_REPLY" // not really used
  val ChannelId = "android_auto"

  var connected = false
  var startMain = false

  def getPackageName(ctx: Context, pkg: String, empty: Boolean): String = {
    val manager = ctx.getPackageManager()
    try {
      Option(manager.getApplicationInfo(pkg, 0)).foreach { appInfo =>
        Option(manager.getApplicationLabel(appInfo)).foreach { s =>
          return s.toString()
        }
      }
    } catch {
      case _: NameNotFoundException =>
    }
    if (empty) {
      ""
    } else {
      pkg
    }
  }

  def getPkgSet(ctx: Context): Set[String] = {
    val pref = ctx.getSharedPreferences(MainActivity.Pref, 0)
    val javaSet = pref.getStringSet(
      MainActivity.KeyPkgs,
      JavaConversions.setAsJavaSet(Set.empty))
    return Set.empty ++ JavaConversions.asScalaSet(javaSet)
  }

  def getFirstString(extras: Bundle, keys: String*): String = {
    keys.foreach { key =>
      {
        Option(extras.getCharSequence(key)) match {
          case Some(s) => return s.toString()
          case None =>
        }
      }
    }
    return ""
  }

  def getNotifText(notif: Notification): String = {
    return getFirstString(
      notif.extras,
      Notification.EXTRA_BIG_TEXT,
      Notification.EXTRA_TEXT,
      Notification.EXTRA_SUMMARY_TEXT,
      Notification.EXTRA_SUB_TEXT,
      Notification.EXTRA_INFO_TEXT,
      Notification.EXTRA_TITLE,
      Notification.EXTRA_TITLE_BIG)
  }
}

class NotificationListener extends NotificationListenerService {
  import NotificationListener.ReplyAction
  import NotificationListener.ReplyKey
  import NotificationListener.ChannelId
  import NotificationListener.connected
  import NotificationListener.startMain

  val PkgSelf = "com.yhsif.autonotif"

  var lastId: Int = 0
  var notifMap: Map[String, Int] = Map()

  override def onListenerConnected(): Unit = {
    connected = true
    if (startMain) {
      startActivity(new Intent(this, classOf[MainActivity]))
    }
  }

  override def onListenerDisconnected(): Unit = {
    connected = false
  }

  override def onNotificationPosted(
      sbn: StatusBarNotification,
      rm: NotificationListenerService.RankingMap): Unit = {
    handleNotif(NotificationListener.getPkgSet(this), sbn)
  }

  override def onNotificationRemoved(sbn: StatusBarNotification): Unit = {
    cancelNotif(NotificationListener.getPkgSet(this), sbn)
  }

  def handleNotif(pkgs: Set[String], sbn: StatusBarNotification): Unit = {
    if (!connected) {
      return
    }
    val pkg = sbn.getPackageName().toLowerCase()
    if (checkPackage(pkgs, pkg, sbn)) {
      val notif = sbn.getNotification()
      val key = sbn.getKey()
      val label = NotificationListener.getPackageName(this, pkg, true)
      val text = NotificationListener.getNotifText(notif)
      if (label == "" || text == "") {
        return
      }

      lazy val channelId = {
        // Lazy create the notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val channel = new NotificationChannel(
            ChannelId,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT)
          channel.setDescription(getString(R.string.channel_desc))
          channel.setShowBadge(false)
          getSystemService(Context.NOTIFICATION_SERVICE)
            .asInstanceOf[NotificationManager]
            .createNotificationChannel(channel)
        }
        ChannelId
      }

      val replyIntent = new Intent().setAction(ReplyAction)
      val replyPendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        replyIntent,
        PendingIntent.FLAG_UPDATE_CURRENT)

      val remoteInput = new RemoteInput.Builder(ReplyKey)
        .setLabel(getString(R.string.notif_reply))
        .build()

      val convBuilder = new UnreadConversation.Builder(label)
        .setReplyAction(replyPendingIntent, remoteInput)
        .addMessage(text)
        .setLatestTimestamp(System.currentTimeMillis())

      val notifBuilder = new NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.icon_notif)
        .setContentText(text)
        .setSound(null)
        .extend(new CarExtender().setUnreadConversation(convBuilder.build()))

      getBitmap(Option(notif.getLargeIcon()), Option(notif.getSmallIcon()))
        .foreach { bitmap =>
          notifBuilder.setLargeIcon(bitmap)
        }

      lastId = lastId + 1
      notifMap(key) = lastId
      NotificationManagerCompat
        .from(this)
        .notify(lastId, notifBuilder.build())
    }
  }

  def cancelNotif(pkgs: Set[String], sbn: StatusBarNotification): Unit = {
    if (!connected) {
      return
    }
    val pkg = sbn.getPackageName().toLowerCase()
    if (checkPackage(pkgs, pkg, sbn)) {
      val key = sbn.getKey()
      notifMap.get(key).foreach { id =>
        NotificationManagerCompat.from(this).cancel(id)
      }
    }
  }

  def checkPackage(
      pkgs: Set[String],
      pkg: String,
      sbn: StatusBarNotification): Boolean =
    pkg != PkgSelf && pkgs(pkg) && !sbn.isOngoing()

  def getBitmap(large: Option[Icon], small: Option[Icon]): Option[Bitmap] = {
    large match {
      case Some(icon) => convertIconToBitmap(icon)
      case None =>
        small match {
          case Some(icon) => convertIconToBitmap(icon)
          case None => None
        }
    }
  }

  def convertIconToBitmap(icon: Icon): Option[Bitmap] = {
    val drawable = icon.loadDrawable(this)

    if (drawable.isInstanceOf[BitmapDrawable]) {
      Option(drawable.asInstanceOf[BitmapDrawable].getBitmap()) match {
        case Some(bitmap) =>
          return Option(bitmap)
        case None =>
      }
    }

    var bitmap: Option[Bitmap] = None
    if (drawable.getIntrinsicWidth() <= 0 ||
        drawable.getIntrinsicHeight() <= 0) {
      bitmap = Option(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    } else {
      bitmap = Option(
        Bitmap.createBitmap(
          drawable.getIntrinsicWidth(),
          drawable.getIntrinsicHeight(),
          Bitmap.Config.ARGB_8888))
    }

    bitmap match {
      case Some(bmp) => {
        val canvas = new Canvas(bmp)
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
        drawable.draw(canvas)
        return Option(bmp)
      }
      case None => None
    }
  }
}
