const HtmlWebpackPlugin = require('html-webpack-plugin')
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const webpack = require('webpack')
const path = require('path')
const FaviconsWebpackPlugin = require("favicons-webpack-plugin");

module.exports = {
  entry: {
    app: './src/index.js',
  },
  performance: {
    hints: false,
    maxEntrypointSize: 512000,
    maxAssetSize: 512000
  },
  output: {
    path: path.resolve("../resources", "dist"),
    filename: '[name].bundle.js',
    assetModuleFilename: 'images/[hash][ext][query]',
    clean: true
  },
  devtool: 'source-map',
  optimization: {
    runtimeChunk: {
      name: 'runtime',
    },
    splitChunks: {
      chunks: 'async',
      minSize: 20000,
      minRemainingSize: 0,
      minChunks: 1,
      maxAsyncRequests: 30,
      maxInitialRequests: 30,
      enforceSizeThreshold: 50000,
      cacheGroups: {
        defaultVendors: {
          test: /[\\/]node_modules[\\/]/,
          priority: -10,
          reuseExistingChunk: true,
        },
        default: {
          minChunks: 2,
          priority: -20,
          reuseExistingChunk: true,
        },
      },
    },
  },
  devServer: {
    hot: true,
    open: true,
    historyApiFallback: {
      index: 'index.html'
    }
  },
  module: {
    rules: [
      {
        test: /\.riot$/,
        exclude: /node_modules/,
        use: [{
          loader: '@riotjs/webpack-loader',
          options: {
            hot: true
          }
        }]
      },
      {
        test: /\.css$/i,
        use: [MiniCssExtractPlugin.loader, 'css-loader'],
      },
      {
        test: /\.png$/,
        type: 'asset/resource',
        generator: {
            filename: 'images/[name][ext][query]'
        }
      }
    ]
  },
  plugins: [
    new MiniCssExtractPlugin(),
    new FaviconsWebpackPlugin({
        logo: "./src/assets/favicon.png",
    }),
    new HtmlWebpackPlugin({
      template: 'src/index.html'
    }),
    new webpack.HotModuleReplacementPlugin(),
  ]
}
