package com.vgram.demo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.app.Fragment;
import android.view.View;
import android.view.ViewGroup;

/**
 * 어플리케이션 FPV 위젯의 Fragment
 */

public class FPVFragment extends Fragment {

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
        View view = inflater.inflate( R.layout.fpv_fragment, container, false );
        return view;
    }
}
