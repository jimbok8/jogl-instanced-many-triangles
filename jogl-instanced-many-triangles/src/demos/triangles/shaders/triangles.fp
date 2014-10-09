#version 410

in vec4    varying_Color;
out vec4    mgl_FragColor;

void main (void) {
	mgl_FragColor = varying_Color; 
} 