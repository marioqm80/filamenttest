package com.example.filamenttestjava.filament.utils;

public class CorUtil {
    public static double[] getCorVarioErico(boolean grayScale, int alfa, double valor, double valorMin, double valorMax, double parte1, double parte2, double parte3){
        double[] output = new double[4];
        if (!grayScale) {
            if (-1.4 <= valor && valor <= -1.1) {
                //return android.graphics.Color.rgb(0, 0, 0);
            }


            int red = 0;//azul escuro
            int green = 0;
            int blue = 255;
        /*

        red = 0;
        green = 255;
        blue = 255;//ciano

        red = 0;
        green = 255;
        blue = 0;// verde

        red = 255;
        green = 255;
        blue = 0;//amarelo

        red = 255;
        green = 0;
        blue = 0;//vermelho

        red = 255;
        green = 0;
        blue = 255;//roxo
        */


            if (valor < valorMin) {
                red = 0;//azul escuro
                green = 0;
                blue = 255;

            } else if (valor >= valorMax) {
                red = 0;
                green = 0;
                blue = 0;//preto
            } else if (valor >= valorMin && valor < parte1) {
                red = 0;//azul escuro
                green = 0;
                blue = 255;
                //parte1-valormin == 255
                //valor-valorMin = x
                //x = (vario * 255)/(parte1-valormin)
                green = green + ((int) Math.floor(((valor - valorMin) * 255d) / (parte1 - valorMin)));
                red = red + ((int) Math.floor(((valor - valorMin) * 255d) / (parte1 - valorMin)));
                blue = blue - ((int) Math.floor(((valor - valorMin) * 255d) / (parte1 - valorMin)));

            } else if (valor >= parte1 && valor < parte2) {
                red = 255;
                green = 255;
                blue = 0;//amarelo
                //parte2-parte1 == 255
                //valor-parte1 ==x
                //x = (valor-parte1)*255/(parte2-parte1)
                green = green - (int) Math.floor(((valor - parte1) * 255) / (parte2 - parte1));


            } else if (valor >= parte2 && valor < parte3) {
                red = 255;
                green = 0;
                blue = 0;//vermelho

                //valorMax-parte2 == 255
                // valor - parte2 == x
                // x = ((vario - 3)*255)/2

                blue = blue + ((int) Math.floor(((valor - parte2) * 255) / (parte3 - parte2)));

            } else if (valor >= parte3 && valor < valorMax) {
                red = 255;
                green = 0;
                blue = 255;//roxo

                red = red - (int) Math.floor(((valor - parte3) * 255) / (valorMax - parte3));
                blue = blue - (int) Math.floor(((valor - parte3) * 255) / (valorMax - parte3));


            }
            output[0] = red/255f;
            output[1] = green/255f;
            output[2] =  blue/255f;
            output[3] = alfa/255f;


            return output;
        }else{
            int mincinza = 100;
            int vcor = 100;
            if (valor < valorMin) {
                vcor = 0;

            } else if (valor >= valorMax) {
                vcor = 0;
            } else if (valor >= valorMin && valor < parte1) {


                vcor = 0;

                vcor = vcor +  ((int) Math.floor(((valor - valorMin) * 155d) / (parte1 - valorMin)));


            } else {
                vcor = 155;
                vcor = vcor - ((int) Math.floor(((valor - parte1) * 155d) / (valorMax - parte1)));

            }
            output[0] = vcor/255f;
            output[1] = vcor/255f;
            output[2] =  vcor/255f;
            output[3] = alfa/255f;

            return output;
        }
    }
}
