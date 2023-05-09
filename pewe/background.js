
function onrequest(req) {
  // This function will be called everytime the browser is about to send out an http or https request.
  // The req variable contains all information about the request.
  // If we return {}  the request will be performed, without any further changes
  // If we return {cancel:true} , the request will be cancelled.
  // If we return {requestHeaders:req.requestHeaders} , any modifications made to the requestHeaders (see below) are sent.

  // script that was used to detect potential spies:

  // req.requestHeaders.forEach(element => {
  //   if (element.name === "Host") {
  //     if (!element.value.includes("bbc")) {
  //       console.log("Potential spy: " + element.value);
  //     }
  //   }
  // });

  let spies = ["chartbeat", "cdn", "securepubads", "2mdn"];
  let headers = req.requestHeaders;

  for (let i = 0; i < headers.length; i++) {
    let header = headers[i];

    // check if the header contains host information
    if (header.name === "Host") {

      // go through the array of potential spies
      for (index in spies) {

        // check if the hostname is one of the spies
        if (header.value.includes(spies[index])) {
          return { cancel: true };
        }
      }
    }

    // hide the browser information
    if (header.name === "User-Agent") {  
      header.value = "Hidden";
    }

    // log the header information
    console.log("Header: " + header.name + " " + header.value);
  }

  // let's do something special if an image is loaded:
  if (req.type == "image") {
    console.log("Ooh, it's a picture!");
  }

  console.log("Loading: " + req.method + " " + req.url + " " + req.type);

  // req also contains an array called requestHeaders containing the name and value of each header.
  // You can access the name and value of the i'th header as req.requestHeaders[i].name and req.requestHeaders[i].value ,
  // with i from 0 up to (but not including) req.requestHeaders.length .

  return { requestHeaders: req.requestHeaders };
}

// no need to change the following, it just makes sure that the above function is called whenever the browser wants to fetch a file
browser.webRequest.onBeforeSendHeaders.addListener(
  onrequest,
  { urls: ["<all_urls>"] },
  ["blocking", "requestHeaders"]
);

