package com.yhsif.autonotif

import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView

class PkgViewHolder(v: View) extends RecyclerView.ViewHolder(v) {
  def setIcon(icon: Drawable) = {
    v.findViewById[ImageView](R.id.icon).setImageDrawable(icon)
  }

  def setName(name: String) = {
    v.findViewById[TextView](R.id.name).setText(name)
  }

  def setBackground(i: Int) = {
    if (i % 2 == 0) {
      v.setBackgroundColor(v.getContext().getColor(R.color.even_background))
    } else {
      v.setBackgroundColor(v.getContext().getColor(R.color.odd_background))
    }
  }
}
