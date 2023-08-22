import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.dongliu.requests.Proxies;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.Requests;
import net.dongliu.requests.Session;

public class Main {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		scrapeReleases();
		iterateReleasesAndCheck();

	}

	public static void scrapeReleases() throws Exception {

		System.out.println("[SNEAKER_CALENDAR] - Getting Releases...");

		String url = "https://gateway.snkraddicted.com/graphql";

		String body = "{\"operationName\":\"getReleases\",\"variables\":{\"client\":\"snkr-web\",\"offset\":0,\"limit\":50,\"sortOrder\":1,\"orderBy\":\"releaseDate\",\"releaseDate\":\"2023-04-21T14:30:32.236Z\",\"releaseDateOrder\":\"after\",\"hasReleaseDate\":true,\"isActive\":true},\"query\":\"query getReleases($isActive: Boolean, $offset: Int, $limit: Int, $orderBy: String, $sortOrder: Float, $client: String, $headline: String, $releaseDate: DateTime, $hasReleaseDate: Boolean, $releaseDateOrder: String, $tags: [String!], $secondOrderBy: String, $secondSortOrder: Float) {\\n  getReleases(\\n    isActive: $isActive\\n    offset: $offset\\n    limit: $limit\\n    orderBy: $orderBy\\n    sortOrder: $sortOrder\\n    client: $client\\n    headline: $headline\\n    releaseDate: $releaseDate\\n    hasReleaseDate: $hasReleaseDate\\n    releaseDateOrder: $releaseDateOrder\\n    tags: $tags\\n    secondOrderBy: $secondOrderBy\\n    secondSortOrder: $secondSortOrder\\n  ) {\\n    items {\\n      _id\\n      content\\n      client\\n      tags\\n      path\\n      createdAt\\n      updatedAt\\n      images {\\n        _id\\n        filename\\n        originalName\\n        altName\\n        altText\\n        createdAt\\n        updatedAt\\n        mimeType\\n        __typename\\n      }\\n      shops {\\n        releaseDate\\n        __typename\\n      }\\n      headline\\n      price\\n      reducedPrice\\n      releaseDate\\n      hasReleaseDate\\n      isActive\\n      __typename\\n    }\\n    totalItems\\n    totalPages\\n    currentPage\\n    __typename\\n  }\\n}\"}";

		Map<String, Object> request = new HashMap<>();

		request.put("Authority", "gateway.snkraddicted.com");
		request.put("Accept", "*/*");
		request.put("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7");
		request.put("Content-Type", "application/json");
		request.put("Origin", "https://www.snkraddicted.com");
		request.put("Referer", "https://www.snkraddicted.com/");
		request.put("Sec-Ch-Ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
		request.put("Sec-Ch-Ua-Mobile", "?0");
		request.put("Sec-Ch-Ua-Platform", "\"Windows\"");
		request.put("Sec-Fetch-Dest", "empty");
		request.put("Sec-Fetch-Mode", "cors");
		request.put("Sec-Fetch-Site", "same-site");
		request.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");

		Session session = Requests.session();
		RawResponse newSession = session.post(url).headers(request).body(body).socksTimeout(60_000)
				.connectTimeout(60_000).send();

		if (newSession.statusCode() != 200) {
			System.out.println("[SNEAKER_CALENDAR] - Retrying: " + +newSession.statusCode());
			Thread.sleep(10000);
		} else {
			String response = newSession.readToText();
			JSONObject responseObject = new JSONObject(new JSONTokener(response));

			System.out.println("[SNEAKER_CALENDAR] - Releases Found: "
					+ responseObject.getJSONObject("data").getJSONObject("getReleases").get("totalItems"));

			// Iterate over Array
			JSONArray releasesArray = responseObject.getJSONObject("data").getJSONObject("getReleases")
					.getJSONArray("items");

			for (int i = 0; i < releasesArray.length(); i++) {
				JSONObject release = releasesArray.getJSONObject(i);

				String id = release.getString("_id");
				String path = release.getString("path");
				String headline = release.getString("headline");
				String price = String.valueOf(release.get("price")) + "€";
				String releaseDate = release.getString("releaseDate");
				String imgUrl = "https://gateway.snkraddicted.com/media/images/"
						+ release.getJSONArray("images").getJSONObject(0).getString("_id") + "/"
						+ release.getJSONArray("images").getJSONObject(0).getString("originalName").replace(" ", "_");

				// Check and add to DB
				addReleaseToDatabase(id, path, headline, scrapeSingleRelease(path, true), price, releaseDate, imgUrl);
			}

		}

	}

	public static void iterateReleasesAndCheck() throws Exception {
		Reader in = new FileReader("releases.csv");
		Iterable<CSVRecord> records = CSVFormat.newFormat(';').withHeader().parse(in);

		for (CSVRecord record : records) {
			String pathRelease = record.get("path");

			scrapeSingleRelease(pathRelease, false);

		}

		in.close();
	}

	public static String scrapeSingleRelease(String path, boolean getArticleIdOnly) throws Exception {

		System.out.println("[SNEAKER_CALENDAR] - Getting Release: " + path);

		String url = "https://gateway.snkraddicted.com/graphql";

		String body = "{\"operationName\":\"GetByPath\",\"variables\":{\"client\":\"snkr-web\",\"globalClient\":\"snkr\",\"path\":\""
				+ path
				+ "\"},\"query\":\"query GetByPath($path: String!, $client: String, $globalClient: String!) {\\n  getDealByPath(path: $path, client: $client) {\\n    _id\\n    content\\n    client\\n    createdAt\\n    updatedAt\\n    headline\\n    subline\\n    voucher\\n    voucherText\\n    voucherFile\\n    buttonLabel\\n    metaTitle\\n    metaDescription\\n    images {\\n      _id\\n      filename\\n      originalName\\n      mimeType\\n      altName\\n      altText\\n      createdAt\\n      updatedAt\\n      __typename\\n    }\\n    price\\n    reducedPrice\\n    strikePrice\\n    priceText\\n    isActive\\n    startAt\\n    endsAt\\n    validTill\\n    rank\\n    dealType\\n    path\\n    bullet1Key\\n    bullet1Val\\n    bullet2Key\\n    bullet2Val\\n    bullet3Key\\n    bullet3Val\\n    bullet4Key\\n    bullet4Val\\n    tags\\n    links {\\n      label\\n      url\\n      type\\n      target\\n      __typename\\n    }\\n    __typename\\n  }\\n  getNodeByPath(path: $path, client: $client) {\\n    _id\\n    path\\n    previewPath\\n    title\\n    redirect\\n    tags\\n    isActive\\n    target {\\n      _id\\n      content\\n      client\\n      createdAt\\n      updatedAt\\n      title\\n      pageTitle\\n      description\\n      img {\\n        _id\\n        filename\\n        originalName\\n        altText\\n        createdAt\\n        updatedAt\\n        altName\\n        mimeType\\n        __typename\\n      }\\n      __typename\\n    }\\n    __typename\\n  }\\n  getReleaseByPath(path: $path, client: $client) {\\n    _id\\n    content\\n    client\\n    createdAt\\n    updatedAt\\n    metaTitle\\n    metaDescription\\n    images {\\n      _id\\n      filename\\n      originalName\\n      mimeType\\n      altName\\n      altText\\n      createdAt\\n      updatedAt\\n      __typename\\n    }\\n    path\\n    headline\\n    price\\n    reducedPrice\\n    releaseDate\\n    hasReleaseDate\\n    isActive\\n    tags\\n    articleId\\n    bullet1Key\\n    bullet2Key\\n    bullet3Key\\n    bullet4Key\\n    bullet1Val\\n    bullet2Val\\n    bullet3Val\\n    bullet4Val\\n    shops {\\n      _id\\n      type\\n      url\\n      price\\n      shop {\\n        _id\\n        previewImage {\\n          _id\\n          filename\\n          originalName\\n          mimeType\\n          altName\\n          altText\\n          createdAt\\n          updatedAt\\n          __typename\\n        }\\n        previewImageDark {\\n          _id\\n          filename\\n          originalName\\n          mimeType\\n          altName\\n          altText\\n          createdAt\\n          updatedAt\\n          __typename\\n        }\\n        headline\\n        tags\\n        isActive\\n        __typename\\n      }\\n      releaseDate\\n      __typename\\n    }\\n    raffles {\\n      _id\\n      type\\n      url\\n      price\\n      shop {\\n        tags\\n        headline\\n        previewImage {\\n          _id\\n          filename\\n          originalName\\n          mimeType\\n          altName\\n          altText\\n          createdAt\\n          updatedAt\\n          __typename\\n        }\\n        _id\\n        isActive\\n        __typename\\n      }\\n      releaseDate\\n      __typename\\n    }\\n    __typename\\n  }\\n  getBlogByPath(path: $path, client: $globalClient) {\\n    _id\\n    content\\n    client\\n    createdAt\\n    updatedAt\\n    isActive\\n    title\\n    tags\\n    path\\n    metaTitle\\n    metaDescription\\n    previewImage {\\n      _id\\n      originalName\\n      filename\\n      altName\\n      altText\\n      __typename\\n    }\\n    __typename\\n  }\\n}\"}";
		Map<String, Object> request = new HashMap<>();

		request.put("Authority", "gateway.snkraddicted.com");
		request.put("Accept", "*/*");
		request.put("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7");
		request.put("Content-Type", "application/json");
		request.put("Origin", "https://www.snkraddicted.com");
		request.put("Referer", "https://www.snkraddicted.com/");
		request.put("Sec-Ch-Ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
		request.put("Sec-Ch-Ua-Mobile", "?0");
		request.put("Sec-Ch-Ua-Platform", "\"Windows\"");
		request.put("Sec-Fetch-Dest", "empty");
		request.put("Sec-Fetch-Mode", "cors");
		request.put("Sec-Fetch-Site", "same-site");
		request.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");

		Session session = Requests.session();
		RawResponse newSession = session.post(url).headers(request).body(body).socksTimeout(60_000)
				.connectTimeout(60_000).send();

		if (newSession.statusCode() != 200) {
			System.out.println("[SNEAKER_CALENDAR] - Failed Single Release: " + +newSession.statusCode());
			Thread.sleep(10000);
		} else {
			String response = newSession.readToText();
			JSONObject responseObject = new JSONObject(new JSONTokener(response));

			if (getArticleIdOnly) {

				try {
					return responseObject.getJSONObject("data").getJSONObject("getReleaseByPath")
							.getString("articleId");

				} catch (Exception e) {
					return "???";

				}
			} else {
				// Get shops
				if (responseObject.getJSONObject("data").getJSONObject("getReleaseByPath").has("shops")) {
					JSONArray shopsArray = responseObject.getJSONObject("data").getJSONObject("getReleaseByPath")
							.getJSONArray("shops");

					for (int i = 0; i < shopsArray.length(); i++) {
						JSONObject shop = shopsArray.getJSONObject(i);

						String id = shop.getString("_id");
						String releaseDate = String.valueOf(shop.get("releaseDate"));

						String urlShop = shop.getString("url");
						String headline = shop.getJSONObject("shop").getString("headline");
						String imgUrl = "https://gateway.snkraddicted.com/media/images/"
								+ shop.getJSONObject("shop").getJSONObject("previewImage").getString("_id") + "/"
								+ shop.getJSONObject("shop").getJSONObject("previewImage").getString("originalName")
										.replace(" ", "_");

						addShopRaffleToDatabase("SHOP", id, headline, urlShop, imgUrl, releaseDate, path);
					}
				}

				// Get raffles
				if (responseObject.getJSONObject("data").getJSONObject("getReleaseByPath").has("raffles")) {
					JSONArray shopsArray = responseObject.getJSONObject("data").getJSONObject("getReleaseByPath")
							.getJSONArray("raffles");

					for (int i = 0; i < shopsArray.length(); i++) {
						JSONObject shop = shopsArray.getJSONObject(i);

						String id = shop.getString("_id");
						String releaseDate = String.valueOf(shop.get("releaseDate"));

						String urlShop = shop.getString("url");
						String headline = shop.getJSONObject("shop").getString("headline");

						String imgUrl = "https://gateway.snkraddicted.com/media/images/"
								+ shop.getJSONObject("shop").getJSONObject("previewImage").getString("_id") + "/"
								+ shop.getJSONObject("shop").getJSONObject("previewImage").getString("originalName")
										.replace(" ", "_");

						addShopRaffleToDatabase("RAFFLE", id, headline, urlShop, imgUrl, releaseDate, path);
					}
				}
			}

		}
		return "";

	}

	public static void addReleaseToDatabase(String id, String path, String headline, String articleId, String price,
			String releaseDate, String imgUrl) throws Exception {
		String content = FileUtils.readFileToString(new File("releases.csv"), "UTF-8");

		if (!content.contains(id)) {
			System.out.println("[SNEAKER_CALENDAR] - 	Added Release: " + id + " - " + headline);

			FileWriter writer = new FileWriter(new File("releases.csv"), true);
			writer.write(id + ";" + path + ";" + headline + ";" + articleId + ";" + price + ";" + releaseDate + ";"
					+ imgUrl + System.lineSeparator());
			writer.close();
		}

	}

	public static void addShopRaffleToDatabase(String type, String id, String headline, String url, String imgUrl,
			String releaseDate, String path) throws Exception {
		String content = FileUtils.readFileToString(new File("releasesShops.csv"), "UTF-8");

		if (!content.contains(id)) {
			System.out.println("[SNEAKER_CALENDAR] - 	Added " + type + ": " + id + " - " + headline);

			FileWriter writer = new FileWriter(new File("releasesShops.csv"), true);
			writer.write(type + ";" + id + ";" + headline + ";" + url + ";" + imgUrl + ";" + releaseDate + ";" + path
					+ System.lineSeparator());
			writer.close();
		}

	}
}
